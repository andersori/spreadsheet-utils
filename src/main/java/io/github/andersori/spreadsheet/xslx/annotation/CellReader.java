package io.github.andersori.spreadsheet.xslx.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.github.andersori.spreadsheet.xslx.Reader;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CellReader {
  Class<? extends Reader<?>> value();
}
