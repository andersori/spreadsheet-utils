package io.github.andersori.spreadsheet.csv;

import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.bean.HeaderColumnNameMappingStrategy;
import com.opencsv.bean.StatefulBeanToCsv;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.SequenceInputStream;
import java.nio.charset.Charset;
import java.util.List;
import org.apache.commons.io.FilenameUtils;
import org.apache.tika.io.IOUtils;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CSVUtils {

  public static <T> Flux<T> read_UTF_8(Class<T> clazz, Resource resource) {
    return Mono.fromCallable(() -> read(clazz, resource.getInputStream(), Charset.forName("UTF-8")))
        .flatMapMany(Flux::fromIterable);
  }

  public static <T> Flux<T> read_ISO_8859_1(Class<T> clazz, Resource resource) {
    return Mono.fromCallable(
            () -> read(clazz, resource.getInputStream(), Charset.forName("ISO-8859-1")))
        .flatMapMany(Flux::fromIterable);
  }

  private static <T> List<T> read(Class<T> clazz, InputStream input, Charset cs) {
    HeaderColumnNameMappingStrategy<T> strategy = new HeaderColumnNameMappingStrategy<T>();
    strategy.setType(clazz);

    CsvToBeanBuilder<T> csvBuilder = new CsvToBeanBuilder<>(new InputStreamReader(input, cs));

    return csvBuilder
        .withMappingStrategy(strategy)
        .withType(clazz)
        .withSeparator(';')
        .withIgnoreLeadingWhiteSpace(true)
        .build()
        .parse();
  }

  public static <T> Flux<T> read_UTF_8(Class<T> clazz, FilePart file) {
    if (!(FilenameUtils.getExtension(file.filename()).equalsIgnoreCase("csv"))) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Só é permitido o envio de CSV.");
    }
    return file.content()
        .map(DataBuffer::asInputStream)
        .reduce(SequenceInputStream::new)
        .flatMapMany(
            input ->
                Mono.fromCallable(
                        () -> read(IOUtils.toByteArray(input), clazz, Charset.forName("UTF-8")))
                    .flatMapMany(Flux::fromIterable));
  }

  public static <T> Flux<T> read_ISO_8859_1(Class<T> clazz, FilePart file) {
    if (!(FilenameUtils.getExtension(file.filename()).equalsIgnoreCase("csv"))) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Só é permitido o envio de CSV.");
    }
    return file.content()
        .map(DataBuffer::asInputStream)
        .reduce(SequenceInputStream::new)
        .flatMapMany(
            input ->
                Mono.fromCallable(
                        () ->
                            read(IOUtils.toByteArray(input), clazz, Charset.forName("ISO-8859-1")))
                    .flatMapMany(Flux::fromIterable));
  }

  private static <T> List<T> read(byte[] bytes, Class<T> clazz, Charset cs) {
    HeaderColumnNameMappingStrategy<T> strategy = new HeaderColumnNameMappingStrategy<T>();
    strategy.setType(clazz);

    CsvToBeanBuilder<T> csvBuilder =
        new CsvToBeanBuilder<>(new InputStreamReader(new ByteArrayInputStream(bytes), cs));

    return csvBuilder
        .withMappingStrategy(strategy)
        .withType(clazz)
        .withSeparator(';')
        .withIgnoreLeadingWhiteSpace(true)
        .build()
        .parse();
  }

  public static <Bean> Mono<Resource> write_ISO_8859_1(Class<Bean> clazz, List<Bean> data) {
    return write(clazz, data, Charset.forName("ISO-8859-1"));
  }

  public static <Bean> Mono<Resource> write_UTF_8(Class<Bean> clazz, List<Bean> data) {
    return write(clazz, data, Charset.forName("UTF-8"));
  }

  public static <Bean> Mono<Resource> write(Class<Bean> clazz, List<Bean> data, Charset cs) {
    if (data.isEmpty()) {
      return Mono.empty();
    }
    return Mono.fromCallable(
        () -> {
          ByteArrayOutputStream inMemory = new ByteArrayOutputStream();
          BufferedWriter inMemoryStream = new BufferedWriter(new OutputStreamWriter(inMemory, cs));

          HeaderColumnNameMappingStrategy<Bean> strategy =
              new HeaderColumnNameMappingStrategy<Bean>();
          strategy.setType(clazz);

          StatefulBeanToCsv<Bean> sbcData =
              new StatefulBeanToCsvBuilder<Bean>(inMemoryStream)
                  .withMappingStrategy(strategy)
                  .withSeparator(';')
                  .build();
          sbcData.write(data);

          return new ByteArrayResource(inMemory.toByteArray());
        });
  }
}
