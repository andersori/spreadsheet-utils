package io.github.andersori.spreadsheet.xml;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.SequenceInputStream;
import java.nio.charset.Charset;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import org.apache.commons.io.FilenameUtils;
import org.apache.tika.io.IOUtils;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

public class XMLUtils {

  public static <T> Mono<T> read_UTF_8(FilePart file, Class<T> clazz) {
    if (!(FilenameUtils.getExtension(file.filename()).equalsIgnoreCase("xml"))) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Só é permitido o envio de XML.");
    }
    return read(file, clazz, Charset.forName("UTF-8"));
  }

  public static <T> Mono<T> read_ISO_8859_1(FilePart file, Class<T> clazz) {
    if (!(FilenameUtils.getExtension(file.filename()).equalsIgnoreCase("xml"))) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Só é permitido o envio de XML.");
    }
    return read(file, clazz, Charset.forName("ISO-8859-1"));
  }

  @SuppressWarnings("unchecked")
  public static <T> Mono<T> read(FilePart file, Class<T> clazz, Charset cs) {
    if (!(FilenameUtils.getExtension(file.filename()).equalsIgnoreCase("xml"))) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Só é permitido o envio de XML.");
    }

    return file.content()
        .map(DataBuffer::asInputStream)
        .reduce(SequenceInputStream::new)
        .flatMap(
            input ->
                Mono.fromCallable(() -> JAXBContext.newInstance(clazz))
                    .flatMap(
                        jaxbContext -> Mono.fromCallable(() -> jaxbContext.createUnmarshaller()))
                    .flatMap(
                        jaxbUnmarshaller ->
                            Mono.fromCallable(
                                () ->
                                    (T)
                                        jaxbUnmarshaller.unmarshal(
                                            new InputStreamReader(
                                                new ByteArrayInputStream(
                                                    IOUtils.toByteArray(input)),
                                                cs)))));
  }

  public static <T> Mono<Resource> write_ISO_8859_1(T data, Class<T> clazz) {
    return write(data, clazz, Charset.forName("ISO-8859-1"));
  }

  public static <T> Mono<Resource> write_UTF_8(T data, Class<T> clazz) {
    return write(data, clazz, Charset.forName("UTF-8"));
  }

  public static <T> Mono<Resource> write(T data, Class<T> clazz, Charset cs) {
    return Mono.fromCallable(() -> JAXBContext.newInstance(clazz))
        .flatMap(jaxbContext -> Mono.fromCallable(() -> jaxbContext.createMarshaller()))
        .flatMap(
            jaxbMarshaller ->
                Mono.fromCallable(
                    () -> {
                      ByteArrayOutputStream inMemory = new ByteArrayOutputStream();
                      BufferedWriter inMemoryStream =
                          new BufferedWriter(new OutputStreamWriter(inMemory, cs));

                      jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
                      jaxbMarshaller.setProperty(Marshaller.JAXB_ENCODING, cs.name());
                      jaxbMarshaller.marshal(data, inMemoryStream);

                      return new ByteArrayResource(inMemory.toByteArray());
                    }));
  }
}
