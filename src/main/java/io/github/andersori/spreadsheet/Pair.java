package io.github.andersori.spreadsheet;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@AllArgsConstructor
public final class Pair<S, T> {
	private final S first;
	private final T second;
	
	public static <S, T> Pair<S, T> of(S first, T second) {
		return new Pair<>(first, second);
	}
}
