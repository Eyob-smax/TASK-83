package com.eventops.security.encryption;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds encryption-related configuration from {@code eventops.encryption.*}
 * in application.yml.
 */
@ConfigurationProperties(prefix = "eventops.encryption")
public class EncryptionProperties {

    private String algorithm;
    private String transformation;
    private int keySize;
    private String secretKey;

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public String getTransformation() {
        return transformation;
    }

    public void setTransformation(String transformation) {
        this.transformation = transformation;
    }

    public int getKeySize() {
        return keySize;
    }

    public void setKeySize(int keySize) {
        this.keySize = keySize;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }
}
