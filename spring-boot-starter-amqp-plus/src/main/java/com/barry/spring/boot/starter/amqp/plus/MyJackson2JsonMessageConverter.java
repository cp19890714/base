package com.barry.spring.boot.starter.amqp.plus;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.AbstractMessageConverter;
import org.springframework.amqp.support.converter.ClassMapper;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.amqp.support.converter.ProjectingMessageConverter;
import org.springframework.amqp.support.converter.SmartMessageConverter;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.MimeType;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Abstract Jackson2 message converter.
 *
 * @author Mark Pollack
 * @author James Carr
 * @author Dave Syer
 * @author Sam Nelson
 * @author Andreas Asplund
 * @author Artem Bilan
 * @author Mohammad Hewedy
 * @author Gary Russell
 * @since 2.1
 */
public class MyJackson2JsonMessageConverter extends AbstractMessageConverter
        implements BeanClassLoaderAware, SmartMessageConverter {

    /**
     * The charset used when converting {@link String} to/from {@code byte[]}.
     */
    public static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;
    protected final Log log = LogFactory.getLog(getClass());
    protected final ObjectMapper objectMapper;
    /**
     * The supported content type; only the subtype is checked, e.g. *&#47;json,
     * *&#47;xml.
     */
    private final MimeType supportedContentType;
    @Nullable
    private ClassMapper classMapper = null;

    private Charset defaultCharset = DEFAULT_CHARSET;

    private boolean typeMapperSet;

    private ClassLoader classLoader = ClassUtils.getDefaultClassLoader();

    private Jackson2JavaTypeMapper javaTypeMapper = new DefaultJackson2JavaTypeMapper();

    private boolean useProjectionForInterfaces;

    private ProjectingMessageConverter projectingConverter;

    private boolean standardCharset;

    private boolean assumeSupportedContentType = true;

    /**
     * Construct with the provided {@link ObjectMapper} instance.
     *
     * @param objectMapper    the {@link ObjectMapper} to use.
     * @param contentType     supported content type when decoding messages, only the subtype
     *                        is checked, e.g. *&#47;json, *&#47;xml.
     * @param trustedPackages the trusted Java packages for deserialization
     * @see DefaultJackson2JavaTypeMapper#setTrustedPackages(String...)
     */
    protected MyJackson2JsonMessageConverter(ObjectMapper objectMapper, MimeType contentType,
            String... trustedPackages) {

        Assert.notNull(objectMapper, "'objectMapper' must not be null");
        Assert.notNull(contentType, "'contentType' must not be null");
        this.objectMapper = objectMapper;
        this.supportedContentType = contentType;
        ((DefaultJackson2JavaTypeMapper) this.javaTypeMapper).setTrustedPackages(trustedPackages);
    }

    @Nullable
    public ClassMapper getClassMapper() {
        return this.classMapper;
    }

    public void setClassMapper(ClassMapper classMapper) {
        this.classMapper = classMapper;
    }

    public String getDefaultCharset() {
        return this.defaultCharset.name();
    }

    /**
     * Specify the default charset to use when converting to or from text-based
     * Message body content. If not specified, the charset will be "UTF-8".
     *
     * @param defaultCharset The default charset.
     */
    public void setDefaultCharset(@Nullable String defaultCharset) {
        this.defaultCharset = (defaultCharset != null) ? Charset.forName(defaultCharset)
                : DEFAULT_CHARSET;
        if (this.defaultCharset.equals(StandardCharsets.UTF_8)) {
            this.standardCharset = true;
        }
    }

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
        if (!this.typeMapperSet) {
            ((DefaultJackson2JavaTypeMapper) this.javaTypeMapper).setBeanClassLoader(classLoader);
        }
    }

    protected ClassLoader getClassLoader() {
        return this.classLoader;
    }

    public Jackson2JavaTypeMapper getJavaTypeMapper() {
        return this.javaTypeMapper;
    }

    public void setJavaTypeMapper(Jackson2JavaTypeMapper javaTypeMapper) {
        Assert.notNull(javaTypeMapper, "'javaTypeMapper' cannot be null");
        this.javaTypeMapper = javaTypeMapper;
        this.typeMapperSet = true;
    }

    /**
     * Whether or not an explicit java type mapper has been provided.
     *
     * @return false if the default type mapper is being used.
     * @see #setJavaTypeMapper(Jackson2JavaTypeMapper)
     * @since 2.2
     */
    public boolean isTypeMapperSet() {
        return this.typeMapperSet;
    }

    /**
     * Return the type precedence.
     *
     * @return the precedence.
     * @see #setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence)
     */
    public Jackson2JavaTypeMapper.TypePrecedence getTypePrecedence() {
        return this.javaTypeMapper.getTypePrecedence();
    }

    /**
     * Set the precedence for evaluating type information in message properties.
     * When using {@code @RabbitListener} at the method level, the framework attempts
     * to determine the target type for payload conversion from the method signature.
     * If so, this type is provided in the
     * {@link MessageProperties#getInferredArgumentType() inferredArgumentType}
     * message property.
     * <p> By default, if the type is concrete (not abstract, not an interface), this will
     * be used ahead of type information provided in the {@code __TypeId__} and
     * associated headers provided by the sender.
     * <p> If you wish to force the use of the  {@code __TypeId__} and associated headers
     * (such as when the actual type is a subclass of the method argument type),
     * set the precedence to {@link Jackson2JavaTypeMapper.TypePrecedence#TYPE_ID}.
     *
     * @param typePrecedence the precedence.
     * @see DefaultJackson2JavaTypeMapper#setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence)
     */
    public void setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence typePrecedence) {
        if (this.typeMapperSet) {
            throw new IllegalStateException("When providing your own type mapper, you should set the precedence on it");
        }
        if (this.javaTypeMapper instanceof DefaultJackson2JavaTypeMapper) {
            ((DefaultJackson2JavaTypeMapper) this.javaTypeMapper).setTypePrecedence(typePrecedence);
        } else {
            throw new IllegalStateException("Type precedence is available with the DefaultJackson2JavaTypeMapper");
        }
    }

    protected boolean isUseProjectionForInterfaces() {
        return this.useProjectionForInterfaces;
    }

    /**
     * Set to true to use Spring Data projection to create the object if the inferred
     * parameter type is an interface.
     *
     * @param useProjectionForInterfaces true to use projection.
     * @since 2.2
     */
    public void setUseProjectionForInterfaces(boolean useProjectionForInterfaces) {
        this.useProjectionForInterfaces = useProjectionForInterfaces;
        if (useProjectionForInterfaces) {
            if (!ClassUtils.isPresent("org.springframework.data.projection.ProjectionFactory", this.classLoader)) {
                throw new IllegalStateException("'spring-data-commons' is required to use Projection Interfaces");
            }
            this.projectingConverter = new ProjectingMessageConverter(this.objectMapper);
        }
    }

    /**
     * By default the supported content type is assumed when there is no contentType
     * property or it is set to the default ('application/octet-stream'). Set to 'false'
     * to revert to the previous behavior of returning an unconverted 'byte[]' when this
     * condition exists.
     *
     * @param assumeSupportedContentType set false to not assume the content type is
     *                                   supported.
     * @since 2.2
     */
    public void setAssumeSupportedContentType(boolean assumeSupportedContentType) {
        this.assumeSupportedContentType = assumeSupportedContentType;
    }

    @Override
    public Object fromMessage(Message message) throws MessageConversionException {
        return fromMessage(message, null);
    }

    /**
     * {@inheritDoc}
     *
     * @param conversionHint The conversionHint must be a {@link ParameterizedTypeReference}.
     */
    @Override
    public Object fromMessage(Message message, @Nullable Object conversionHint) throws MessageConversionException {
        Object content = null;
        MessageProperties properties = message.getMessageProperties();
        if (properties != null) {
            String contentType = properties.getContentType();
            if ((this.assumeSupportedContentType // NOSONAR Boolean complexity
                    && (contentType == null || contentType.equals(MessageProperties.DEFAULT_CONTENT_TYPE)))
                    || (contentType != null && contentType.contains(this.supportedContentType.getSubtype()))) {
                String encoding = properties.getContentEncoding();
                if (encoding == null) {
                    encoding = getDefaultCharset();
                }
                content = doFromMessage(message, conversionHint, properties, encoding);
            } else {
                if (this.log.isWarnEnabled()) {
                    this.log.warn("Could not convert incoming message with content-type ["
                            + contentType + "], '" + this.supportedContentType.getSubtype() + "' keyword missing.");
                }
            }
        }
        if (content == null) {
            content = message.getBody();
        }
        return content;
    }

    private Object doFromMessage(Message message, Object conversionHint, MessageProperties properties,
            String encoding) {

        Object content;
        try {
            JavaType inferredType = this.javaTypeMapper.getInferredType(properties);
            if (inferredType != null && this.useProjectionForInterfaces && inferredType.isInterface()
                    && !inferredType.getRawClass().getPackage().getName().startsWith("java.util")) { // List etc
                content = this.projectingConverter.convert(message, inferredType.getRawClass());
            } else if (conversionHint instanceof ParameterizedTypeReference) {
                content = convertBytesToObject(message.getBody(), encoding,
                        this.objectMapper.getTypeFactory().constructType(
                                ((ParameterizedTypeReference<?>) conversionHint).getType()));
            } else if (getClassMapper() == null) {
                JavaType targetJavaType = getJavaTypeMapper()
                        .toJavaType(message.getMessageProperties());
                content = convertBytesToObject(message.getBody(),
                        encoding, targetJavaType);
            } else {
                try {
                    Class<?> targetClass = getClassMapper().toClass(// NOSONAR never null
                            message.getMessageProperties());
                    content = convertBytesToObject(message.getBody(),
                            encoding, targetClass);
                } catch (MessageConversionException ex) {
                    return new String(message.getBody());
                }

            }
        } catch (IOException e) {
            throw new MessageConversionException(
                    "Failed to convert Message content", e);
        }
        return content;
    }

    private Object convertBytesToObject(byte[] body, String encoding, JavaType targetJavaType) throws IOException {
        String contentAsString = new String(body, encoding);
        return this.objectMapper.readValue(contentAsString, targetJavaType);
    }

    private Object convertBytesToObject(byte[] body, String encoding, Class<?> targetClass) throws IOException {
        String contentAsString = new String(body, encoding);
        return this.objectMapper.readValue(contentAsString, this.objectMapper.constructType(targetClass));
    }

    @Override
    protected Message createMessage(Object objectToConvert, MessageProperties messageProperties)
            throws MessageConversionException {

        return createMessage(objectToConvert, messageProperties, null);
    }

    @Override
    protected Message createMessage(Object objectToConvert, MessageProperties messageProperties,
            @Nullable Type genericType) throws MessageConversionException {

        byte[] bytes;
        try {
            if (this.standardCharset) {
                bytes = this.objectMapper.writeValueAsBytes(objectToConvert);
            } else {
                String jsonString = this.objectMapper
                        .writeValueAsString(objectToConvert);
                bytes = jsonString.getBytes(getDefaultCharset());
            }
        } catch (IOException e) {
            throw new MessageConversionException("Failed to convert Message content", e);
        }
        messageProperties.setContentType(this.supportedContentType.toString());
        messageProperties.setContentEncoding(getDefaultCharset());
        messageProperties.setContentLength(bytes.length);

        if (getClassMapper() == null) {
            JavaType type = this.objectMapper.constructType(
                    genericType == null ? objectToConvert.getClass() : genericType);
            if (genericType != null && !type.isContainerType()
                    && Modifier.isAbstract(type.getRawClass().getModifiers())) {
                type = this.objectMapper.constructType(objectToConvert.getClass());
            }
            getJavaTypeMapper().fromJavaType(type, messageProperties);
        } else {
            getClassMapper().fromClass(objectToConvert.getClass(), messageProperties); // NOSONAR never null
        }

        return new Message(bytes, messageProperties);
    }

}
