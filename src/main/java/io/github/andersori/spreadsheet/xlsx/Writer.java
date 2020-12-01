package io.github.andersori.spreadsheet.xlsx;

import org.apache.poi.ss.usermodel.Cell;

public interface Writer<T> {

  void write(T data, Cell cell);
}
