package com.archer.jdknet.http.multipart;

public class Multipart {

    private MultipartType type;

    private String name;

    private String fileName;

    private byte[] content;

    public MultipartType getType() {
        return type;
    }

    public void setType(MultipartType type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public byte[] getContent() {
        return content;
    }

    public void setContent(byte[] content) {
        this.content = content;
    }
}
