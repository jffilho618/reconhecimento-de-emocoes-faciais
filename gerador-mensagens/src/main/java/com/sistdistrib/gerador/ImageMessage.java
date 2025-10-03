package com.sistdistrib.gerador;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Base64;

public class ImageMessage {
    @JsonProperty("type")
    private String type;

    @JsonProperty("filename")
    private String filename;

    @JsonProperty("data")
    private String dataBase64; // Mudança para Base64

    @JsonProperty("timestamp")
    private long timestamp;

    public ImageMessage() {}

    public ImageMessage(String type, String filename, byte[] data) {
        this.type = type;
        this.filename = filename;
        this.dataBase64 = Base64.getEncoder().encodeToString(data); // Codificar em Base64
        this.timestamp = System.currentTimeMillis();
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    @JsonProperty("data")
    public String getDataBase64() {
        return dataBase64;
    }

    @JsonProperty("data")
    public void setDataBase64(String dataBase64) {
        this.dataBase64 = dataBase64;
    }

    // Método de conveniência para obter bytes (não serializado)
    @JsonIgnore
    public byte[] getData() {
        return Base64.getDecoder().decode(dataBase64);
    }

    // Método de conveniência para definir bytes (não serializado)
    @JsonIgnore
    public void setData(byte[] data) {
        this.dataBase64 = Base64.getEncoder().encodeToString(data);
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}