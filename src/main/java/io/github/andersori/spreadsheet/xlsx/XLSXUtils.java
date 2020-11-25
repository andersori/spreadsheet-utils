package io.github.andersori.spreadsheet.xlsx;

import com.google.common.collect.Iterables;
import io.github.andersori.spreadsheet.Pair;
import io.github.andersori.spreadsheet.xlsx.annotation.CellProps;
import io.github.andersori.spreadsheet.xlsx.annotation.CellReader;
import io.github.andersori.spreadsheet.xlsx.annotation.CellWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.NumberToTextConverter;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class XLSXUtils {

  public static <T> Mono<Resource> write(Iterable<T> data) {

    if (Iterables.size(data) == 0) {
      return Mono.empty();
    }

    return Mono.defer(
        () -> {
          try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet();

            DataFormat format = workbook.createDataFormat();
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(format.getFormat("dd!/mm!/yyyy"));

            int rowNum = 0;
            Row header = sheet.createRow(rowNum++);

            Class<?> clazz = Iterables.get(data, 0).getClass();

            for (Field field : Iterables.get(data, 0).getClass().getDeclaredFields()) {
              CellProps column = field.getDeclaredAnnotation(CellProps.class);

              if (column == null) {
                throw new IllegalArgumentException(
                    "Todos os atributos do seu objeto deve conter a annotation @ColumnName");
              }

              if (column.position() < 0 || column.position() >= clazz.getDeclaredFields().length) {
                throw new IllegalArgumentException(
                    "A posição " + column.position() + " é invalida");
              } else {
                Cell cell = header.createCell(column.position());
                cell.setCellValue(column.value().toUpperCase());
              }
            }

            for (T obj : data) {
              Row row = sheet.createRow(rowNum++);
              for (Field field : obj.getClass().getDeclaredFields()) {
                Cell cell = row.createCell(field.getDeclaredAnnotation(CellProps.class).position());
                field.setAccessible(true);

                try {
                  if (field.get(obj) != null) {
                    if (field.getType().equals(UUID.class)) {
                      cell.setCellValue(((UUID) field.get(obj)).toString());
                    } else if (field.getType().equals(Integer.class)) {
                      cell.setCellValue((Integer) field.get(obj));
                    } else if (field.getType().equals(Double.class)) {
                      cell.setCellValue((Double) field.get(obj));
                    } else if (field.getType().equals(Long.class)) {
                      cell.setCellValue((Long) field.get(obj));
                    } else if (field.getType().equals(Boolean.class)) {
                      cell.setCellValue((Boolean) field.get(obj));
                    } else if (field.getType().equals(String.class)) {
                      cell.setCellValue((String) field.get(obj));
                    } else if (field.getType().equals(LocalDate.class)) {
                      cell.setCellStyle(dateStyle);
                      cell.setCellValue(
                          Date.from(
                              ((LocalDate) field.get(obj))
                                  .atStartOfDay(ZoneId.systemDefault())
                                  .toInstant()));
                    } else if (field.getType().equals(LocalDateTime.class)) {
                      cell.setCellValue(
                          Date.from(
                              ((LocalDateTime) field.get(obj))
                                  .atZone(ZoneId.systemDefault())
                                  .toInstant()));
                    } else if (field.getType().isEnum()) {
                      cell.setCellValue((String) field.get(obj).toString());
                    } else {

                      CellWriter cellWriter = field.getDeclaredAnnotation(CellWriter.class);

                      if (cellWriter != null) {
                        try {
                          Writer writer = cellWriter.value().getDeclaredConstructor().newInstance();
                          Method method =
                              cellWriter.value().getDeclaredMethod("write", Object.class);

                          cell.setCellValue((String) method.invoke(writer, field.get(obj)));
                        } catch (InstantiationException
                            | InvocationTargetException
                            | NoSuchMethodException
                            | SecurityException e) {
                        }
                      } else {
                        throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Não é possivel salvar o tipo de dado informado "
                                + field.getType().getCanonicalName());
                      }
                    }
                  } else {
                    cell.setBlank();
                  }
                } catch (IllegalArgumentException | IllegalAccessException e) {
                  return Mono.error(e);
                }
              }
            }

            try {
              File fileCreated =
                  File.createTempFile(UUID.randomUUID().toString() + "-generate", ".xslx");
              FileOutputStream out = new FileOutputStream(fileCreated);

              workbook.write(out);
              ByteArrayResource res =
                  new ByteArrayResource(Files.readAllBytes(fileCreated.toPath()));
              fileCreated.delete();

              return Mono.just(res);
            } catch (IOException e) {
              return Mono.error(e);
            }

          } catch (IOException e) {
            return Mono.error(e);
          }
        });
  }

  public static <T> Flux<T> read(FilePart file, Class<T> clazz) {

    for (Field field : clazz.getDeclaredFields()) {
      CellProps prop = field.getDeclaredAnnotation(CellProps.class);
      if (prop == null)
        throw new IllegalArgumentException(
            "Todos os atributos da sua clase deve conter a annotation @ColumnName");
    }

    try {
      return Mono.just(
              File.createTempFile(UUID.randomUUID().toString() + "-alteracao-proposta", ".xslx"))
          .doOnNext(fileCreated -> file.transferTo(fileCreated))
          .flatMapMany(
              fileCreated -> {
                try (XSSFWorkbook workbook = new XSSFWorkbook(new FileInputStream(fileCreated))) {

                  Set<Pair<Field, CellProps>> fieldProp =
                      Stream.of(clazz.getDeclaredFields())
                          .peek(field -> field.setAccessible(true))
                          .map(
                              field -> Pair.of(field, field.getDeclaredAnnotation(CellProps.class)))
                          .collect(Collectors.toSet());

                  return Flux.fromStream(
                          StreamSupport.stream(workbook.getSheetAt(0).spliterator(), false))
                      .doOnNext(
                          row -> {
                            if (row.getRowNum() == 0) {
                              fieldProp.stream()
                                  .map(Pair::getSecond)
                                  .forEach(
                                      prop -> {
                                        Cell cell = row.getCell(prop.position());
                                        if (cell != null) {
                                          String cellName = "";
                                          try {
                                            cellName = cell.getStringCellValue();
                                          } catch (RuntimeException e) {
                                            throw new RuntimeException(
                                                "Erro na analise do cabeçalho");
                                          }

                                          if (!cellName.equalsIgnoreCase(prop.value())) {
                                            throw new RuntimeException(
                                                "A coluna na posição "
                                                    + prop.position()
                                                    + " deve ser "
                                                    + prop.value()
                                                    + ".");
                                          }
                                        } else {
                                          throw new RuntimeException(
                                              "Coluna "
                                                  + prop.value()
                                                  + " na posição "
                                                  + prop.position()
                                                  + " não encontrada.");
                                        }
                                      });
                            }
                          })
                      .doOnError(ex -> fileCreated.delete())
                      .filter(row -> row.getRowNum() != 0)
                      .map(
                          row -> {
                            T info = null;

                            try {
                              info = clazz.getConstructor().newInstance();
                            } catch (Exception e) {
                              throw new RuntimeException(
                                  "Erro ao instanciar objeto para leitura do XLSX");
                            }

                            for (Pair<Field, CellProps> pair : fieldProp) {
                              Cell cell = row.getCell(pair.getSecond().position());

                              if (cell == null
                                  || (cell != null && cell.getCellType().equals(CellType.BLANK))) {
                                continue;
                              }

                              Class<?> clazzField = pair.getFirst().getType();

                              try {

                                CellReader cellReader =
                                    pair.getFirst().getDeclaredAnnotation(CellReader.class);

                                if (clazzField.isEnum() || cellReader != null) {

                                  Reader<?> reader =
                                      cellReader.value().getDeclaredConstructor().newInstance();

                                  Method method =
                                      cellReader.value().getDeclaredMethod("read", Cell.class);

                                  pair.getFirst().set(info, method.invoke(reader, cell));
                                } else if (clazzField.isAssignableFrom(Long.class)) {
                                  pair.getFirst()
                                      .set(
                                          info,
                                          Long.parseLong(
                                              NumberToTextConverter.toText(
                                                  cell.getNumericCellValue())));
                                } else if (clazzField.isAssignableFrom(Double.class)) {
                                  pair.getFirst()
                                      .set(
                                          info,
                                          Double.parseDouble(
                                              NumberToTextConverter.toText(
                                                  cell.getNumericCellValue())));
                                } else if (clazzField.isAssignableFrom(String.class)) {
                                  if (cell.getCellType().equals(CellType.STRING)) {
                                    pair.getFirst().set(info, cell.getStringCellValue());
                                  } else if (cell.getCellType().equals(CellType.NUMERIC)) {
                                    pair.getFirst()
                                        .set(
                                            info,
                                            NumberToTextConverter.toText(
                                                cell.getNumericCellValue()));
                                  } else {
                                    throw new RuntimeException(
                                        "Não foi possível atribuir o valor da linha "
                                            + row.getRowNum()
                                            + " coluna "
                                            + pair.getSecond().position()
                                            + " na variavel "
                                            + pair.getFirst().getName());
                                  }
                                } else if (clazzField.isAssignableFrom(LocalDate.class)) {
                                  pair.getFirst()
                                      .set(
                                          info,
                                          cell.getDateCellValue()
                                              .toInstant()
                                              .atZone(ZoneId.systemDefault())
                                              .toLocalDate());
                                } else if (clazzField.isPrimitive()) {
                                  throw new RuntimeException(
                                      "Não é permitido o uso de tipo primitivo para leitura de xlsx");
                                } else {
                                  throw new RuntimeException(
                                      clazzField.getCanonicalName() + " não possui implementação");
                                }
                              } catch (IllegalArgumentException | IllegalAccessException e) {
                                throw new RuntimeException("Erro ao ler dados do xlsx", e);
                              } catch (InstantiationException
                                  | InvocationTargetException
                                  | NoSuchMethodException
                                  | SecurityException e) {
                                throw new RuntimeException(
                                    "Erro ao ler dados customizados do xlsx", e);
                              }
                            }

                            return info;
                          })
                      .doOnComplete(
                          () -> {
                            fileCreated.delete();
                          })
                      .doOnError(ex -> fileCreated.delete());
                } catch (IOException e) {
                  fileCreated.delete();
                  return Flux.error(e);
                }
              });
    } catch (IOException e) {
      return Flux.error(e);
    }
  }
}
