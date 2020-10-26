package io.github.andersori.spreadsheet.xslx;

import org.apache.poi.ss.usermodel.Cell;

public interface Reader<T> {

  T read(Cell cell) throws RuntimeException;
}
