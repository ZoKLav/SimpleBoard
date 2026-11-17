package com.zoeykl.simpleboard;

import ai.onnxruntime.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Serialized offline transformer inference with bounded, dictionary-constrained word decoding. */
public final class NeuralEngine implements AutoCloseable {
    private static final int WIDTH=4,MAX_WORD_TOKENS=3;
    private final OrtEnvironment environment;
    private final OrtSession session;
    private final OnnxTensor emptyCache;
    private final NeuralTokenizer tokenizer;
    private final Node root=new Node();
    private final boolean[] boundary=new boolean[49152];
    private OrtSession.RunOptions running;
    private volatile boolean cancelled;
    private static final class Node {String word;final Map<Integer,Node> children=new HashMap<>();}
    private static final class Beam {final Node node;final int token,parent;final double score;Beam(Node n,int t,int p,double s){node=n;token=t;parent=p;score=s;}}
    private static final class Frame implements AutoCloseable {
        final OrtSession.Result result;final int length;final float[][] logits;
        Frame(OrtSession.Result r,int n)throws OrtException{result=r;length=n;logits=(float[][])r.get("logits").get().getValue();}
        public void close(){result.close();}
    }
    public NeuralEngine(ByteBuffer model,InputStream tokens,InputStream words,InputStream boundaries)throws Exception{
        tokenizer=new NeuralTokenizer(tokens);
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(words,StandardCharsets.UTF_8))){
            String line;while((line=reader.readLine())!=null){String[] fields=line.split("\t");if(fields.length!=2)continue;
                Node node=root;for(String id:fields[1].split(","))node=node.children.computeIfAbsent(Integer.parseInt(id),k->new Node());node.word=fields[0];
            }
        }
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(boundaries,StandardCharsets.UTF_8))){for(String id:reader.readLine().split(","))boundary[Integer.parseInt(id)]=true;}
        environment=OrtEnvironment.getEnvironment(OrtLoggingLevel.ORT_LOGGING_LEVEL_ERROR,"simpleboard");environment.setTelemetry(false);
        OrtSession created=null;OnnxTensor cache=null;
        try(OrtSession.SessionOptions options=new OrtSession.SessionOptions()){
            options.setIntraOpNumThreads(1);options.setInterOpNumThreads(1);
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            options.addConfigEntry("session.intra_op.allow_spinning","0");options.addConfigEntry("session.inter_op.allow_spinning","0");
            created=environment.createSession(model,options);
            cache=OnnxTensor.createTensor(environment,FloatBuffer.allocate(0),new long[]{1,3,0,64});
        }catch(Exception|Error error){if(cache!=null)cache.close();if(created!=null)created.close();throw error;}
        session=created;emptyCache=cache;
    }
    public synchronized void cancel(){cancelled=true;if(running!=null)try{running.setTerminate(true);}catch(OrtException ignored){}}
    public synchronized void beginRequest(){cancelled=false;}
    private Frame forward(long[][] tokens,Frame past,int[] parents,OrtSession.RunOptions options)throws Exception{
        if(cancelled)return null;
        int batch=tokens.length,seq=tokens[0].length,previous=past==null?0:past.length;
        long[][] mask=new long[batch][previous+seq],positions=new long[batch][seq];
        for(int row=0;row<batch;row++){Arrays.fill(mask[row],1);for(int i=0;i<seq;i++)positions[row][i]=previous+i;}
        Map<String,OnnxTensor> input=new HashMap<>();List<OnnxTensor> owned=new ArrayList<>();
        try{
            OnnxTensor ids=OnnxTensor.createTensor(environment,tokens);owned.add(ids);input.put("input_ids",ids);
            OnnxTensor attention=OnnxTensor.createTensor(environment,mask);owned.add(attention);input.put("attention_mask",attention);
            OnnxTensor position=OnnxTensor.createTensor(environment,positions);owned.add(position);input.put("position_ids",position);
            for(int layer=0;layer<30;layer++)for(String kind:new String[]{"key","value"}){
                OnnxTensor value=emptyCache;
                if(past!=null){
                    FloatBuffer source=((OnnxTensor)past.result.get("present."+layer+"."+kind).get()).getFloatBuffer();
                    int size=3*previous*64;FloatBuffer selected=FloatBuffer.allocate(batch*size);
                    for(int parent:parents){FloatBuffer row=source.duplicate();row.position(parent*size);row.limit((parent+1)*size);selected.put(row);}
                    selected.flip();value=OnnxTensor.createTensor(environment,selected,new long[]{batch,3,previous,64});owned.add(value);
                }
                input.put("past_key_values."+layer+"."+kind,value);
            }
            if(cancelled)return null;
            OrtSession.Result result=session.run(input,options);
            try{return new Frame(result,previous+seq);}catch(Exception|Error e){result.close();throw e;}
        }finally{for(OnnxTensor tensor:owned)tensor.close();}
    }
    public Map<String,Double> predict(String context)throws Exception{
        Map<String,Double> completed=new HashMap<>();Frame frame=null;
        try(OrtSession.RunOptions options=new OrtSession.RunOptions()){
            synchronized(this){if(cancelled)return completed;running=options;}
            try{
                frame=forward(new long[][]{tokenizer.encode(context,32)},null,null,options);if(frame==null)return completed;
                List<Beam> beams=expand(root,frame.logits[0],0,0,new ArrayList<Beam>());
                for(int depth=0;depth<MAX_WORD_TOKENS&&!beams.isEmpty()&&!cancelled;depth++){
                    beams.sort((a,b)->Double.compare(b.score,a.score));if(beams.size()>WIDTH)beams=new ArrayList<>(beams.subList(0,WIDTH));
                    long[][] ids=new long[beams.size()][1];int[] parents=new int[beams.size()];for(int i=0;i<beams.size();i++){ids[i][0]=beams.get(i).token;parents[i]=beams.get(i).parent;}
                    Frame next=forward(ids,frame,parents,options);frame.close();frame=next;if(frame==null)break;
                    List<Beam> branches=new ArrayList<>();
                    for(int i=0;i<beams.size();i++){
                        Beam beam=beams.get(i);float[] logits=frame.logits[i];double normalizer=logSum(logits,false);
                        if(beam.node.word!=null){double score=beam.score+logSum(logits,true)-normalizer;if(Double.isFinite(score))completed.put(beam.node.word,score);}
                        if(depth+1<MAX_WORD_TOKENS)expand(beam.node,logits,i,beam.score,branches);
                    }
                    beams=branches;
                }
                if(cancelled)completed.clear();return completed;
            }finally{synchronized(this){running=null;}if(frame!=null)frame.close();}
        }
    }
    private List<Beam> expand(Node parent,float[] logits,int row,double prior,List<Beam> out){
        double normalizer=logSum(logits,false);
        PriorityQueue<Beam> top=new PriorityQueue<>(Comparator.comparingDouble(b->b.score));
        for(Map.Entry<Integer,Node> item:parent.children.entrySet()){
            double score=prior+logits[item.getKey()]-normalizer;if(!Double.isFinite(score))continue;
            if(top.size()<WIDTH||score>top.peek().score){top.add(new Beam(item.getValue(),item.getKey(),row,score));if(top.size()>WIDTH)top.poll();}
        }
        out.addAll(top);return out;
    }
    private double logSum(float[] values,boolean boundariesOnly){
        double max=Double.NEGATIVE_INFINITY;for(int i=0;i<values.length;i++)if(!boundariesOnly||boundary[i])max=Math.max(max,values[i]);
        double sum=0;for(int i=0;i<values.length;i++)if(!boundariesOnly||boundary[i])sum+=Math.exp(values[i]-max);return max+Math.log(sum);
    }
    @Override public void close()throws OrtException{emptyCache.close();session.close();}
}
