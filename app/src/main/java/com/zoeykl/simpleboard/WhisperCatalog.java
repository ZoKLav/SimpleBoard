package com.zoeykl.simpleboard;

final class WhisperCatalog {
    static final String REVISION="5359861c739e955e79d9a303bcbc70fb988958b1";
    static final class Model {
        final String file,label,sha256;final long bytes;
        Model(String file,String label,long bytes,String sha){this.file=file;this.label=label;this.bytes=bytes;sha256=sha;}
        String url(){return "https://huggingface.co/ggerganov/whisper.cpp/resolve/"+REVISION+"/"+file+"?download=true";}
        String id(){return java.util.UUID.nameUUIDFromBytes(sha256.getBytes(java.nio.charset.StandardCharsets.US_ASCII)).toString();}
    }
    static final Model[] MODELS={
        new Model("ggml-base.bin","Whisper base · 142 MiB",147951465L,"60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe"),
        new Model("ggml-large-v3-turbo-q5_0.bin","Whisper turbo Q5 · 547 MiB",574041195L,"394221709cd5ad1f40c46e6031ca61bce88931e6e088c188294c6d5a55ffa7e2"),
        new Model("ggml-small.bin","Whisper small · 466 MiB",487601967L,"1be3a9b2063867b937e64e2ec7483364a79917e157fa98c5d94b5c1fffea987b")
    };
    private WhisperCatalog(){}
}
