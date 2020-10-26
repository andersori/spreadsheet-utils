package io.github.andersori.spreadsheet.xslx.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.github.andersori.spreadsheet.xslx.Writer;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CellWriter {
  Class<? extends Writer> value();
}
