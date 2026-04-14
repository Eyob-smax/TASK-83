package com.eventops.security.encryption;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * JPA attribute converter that transparently encrypts String fields on write
 * and decrypts byte[] columns on read.
 *
 * <p>Entity fields annotated with {@code @Convert(converter = EncryptedFieldConverter.class)}
 * are declared as {@code String} in Java but stored as {@code VARBINARY} in MySQL.</p>
 *
 * <p>Because JPA converters are not Spring-managed beans by default, this class
 * uses a static {@link ApplicationContextHolder} to obtain the
 * {@link EncryptionService} instance from the Spring context.</p>
 */
@Converter
public class EncryptedFieldConverter implements AttributeConverter<String, byte[]> {

    @Override
    public byte[] convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        return getEncryptionService().encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(byte[] dbData) {
        if (dbData == null) {
            return null;
        }
        return getEncryptionService().decrypt(dbData);
    }

    private EncryptionService getEncryptionService() {
        return ApplicationContextHolder.getBean(EncryptionService.class);
    }

    /**
     * Static holder that captures the Spring {@link ApplicationContext} so
     * non-Spring-managed objects (such as JPA converters) can look up beans.
     *
     * <p>Registered as a {@link Component} so Spring injects the context
     * via {@link org.springframework.context.ApplicationContextAware}.</p>
     */
    @Component
    public static class ApplicationContextHolder implements org.springframework.context.ApplicationContextAware {

        private static ApplicationContext context;

        @Override
        public void setApplicationContext(ApplicationContext applicationContext) {
            context = applicationContext;
        }

        public static <T> T getBean(Class<T> clazz) {
            if (context == null) {
                throw new IllegalStateException(
                        "Spring ApplicationContext has not been initialised. "
                                + "Ensure ApplicationContextHolder is registered as a bean.");
            }
            return context.getBean(clazz);
        }
    }
}
