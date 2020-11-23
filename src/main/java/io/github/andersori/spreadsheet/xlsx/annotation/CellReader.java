package io.github.andersori.spreadsheet.xlsx.annotation;

import io.github.andersori.spreadsheet.xlsx.Reader;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CellReader {
  Class<? extends Reader<?>> value();
}
