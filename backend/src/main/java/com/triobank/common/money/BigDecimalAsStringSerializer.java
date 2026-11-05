package com.triobank.common.money;

import java.math.BigDecimal;
import org.springframework.boot.jackson.JacksonComponent;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * Writes every {@code BigDecimal} as a string and keeps its scale, so a rate {@code 4.0000}
 * stays {@code "4.0000"}. Money fields use {@link Money} instead.
 */
@JacksonComponent
public class BigDecimalAsStringSerializer extends StdSerializer<BigDecimal> {

    public BigDecimalAsStringSerializer() {
        super(BigDecimal.class);
    }

    @Override
    public void serialize(BigDecimal value, JsonGenerator gen, SerializationContext context) throws JacksonException {
        gen.writeString(value.toPlainString());
    }

}
