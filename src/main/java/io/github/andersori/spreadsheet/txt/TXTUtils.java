package io.github.andersori.spreadsheet.txt;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.SequenceInputStream;
import java.nio.charset.Charset;
import java.util.function.Function;
import org.apache.tika.io.IOUtils;
import org.apache.tika.parser.txt.TXTParser;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

public class TXTUtils {

  public static Mono<Resource> decode(FilePart file, Charset current, Charset target) {
    return file.content()
        .map(DataBuffer::asInputStream)
        .reduce(SequenceInputStream::new)
        .flatMap(
            input ->
                Mono.fromCallable(() -> new String(input.readAllBytes(), current).getBytes(target)))
        .doOnNext(bytes -> System.out.println(new String(bytes)))
        .flatMap(
            bytes ->
                Mono.fromCallable(
                    () ->
                        new ByteArrayResource(
                            IOUtils.toByteArray(
                                new EncodedResource(new ByteArrayResource(bytes), target)
                                    .getReader()))));
  }

  public static Mono<Resource> decode(Resource file, Charset current, Charset target) {
	  
	  new TXTParser(encodingDetector)
    return Mono.fromCallable(() -> file.getInputStream())
        .flatMap(
            input ->
                Mono.fromCallable(() -> new String(input.readAllBytes(), current).getBytes(target)))
        .doOnNext(bytes -> System.out.println(new String(bytes)))
        .flatMap(
            bytes ->
                Mono.fromCallable(
                    () ->
                        new ByteArrayResource(
                            IOUtils.toByteArray(
                                new EncodedResource(new ByteArrayResource(bytes), target)
                                    .getReader()))));
  }

  private static Function<InputStream, Mono<Resource>> replace(
      String oldCharacter, String newCharacter) {
    return input ->
        Mono.fromCallable(
                () ->
                    new BufferedReader(new InputStreamReader(input))
                        .lines()
                        .map(line -> line.replace(oldCharacter, newCharacter))
                        .reduce(
                            "",
                            (a, b) -> {
                              if (a.isBlank()) {
                                return b;
                              }
                              return a + "\n" + b;
                            }))
            .flatMap(text -> Mono.fromCallable(() -> new ByteArrayResource(text.getBytes())));
  }

  public static Mono<Resource> replace(FilePart file, String oldCharacter, String newCharacter) {
    return file.content()
        .map(DataBuffer::asInputStream)
        .reduce(SequenceInputStream::new)
        .flatMap(replace(oldCharacter, newCharacter));
  }

  public static Mono<Resource> replace(Resource file, String oldCharacter, String newCharacter) {
    return Mono.fromCallable(() -> file.getInputStream())
        .flatMap(replace(oldCharacter, newCharacter));
  }
}
