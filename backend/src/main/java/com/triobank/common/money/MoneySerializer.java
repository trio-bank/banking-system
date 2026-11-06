package com.triobank.common.money;

import java.math.BigDecimal;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * Writes money as a string with 2 decimals. Use it through {@link Money}.
 */
public class MoneySerializer extends StdSerializer<BigDecimal> {

    public MoneySerializer() {
        super(BigDecimal.class);
    }

    @Override
    public void serialize(BigDecimal value, JsonGenerator gen, SerializationContext context) throws JacksonException {
        gen.writeString(MoneyUtils.normalize(value).toPlainString());
    }

}
