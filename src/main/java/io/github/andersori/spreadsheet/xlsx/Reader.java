package io.github.andersori.spreadsheet.xlsx;

import org.apache.poi.ss.usermodel.Cell;

public interface Reader<T> {

  T read(Cell cell) throws RuntimeException;
}
