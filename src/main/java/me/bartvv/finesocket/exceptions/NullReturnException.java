package me.bartvv.finesocket.exceptions;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class NullReturnException extends Exception {
	
	private String message;

	private static final long serialVersionUID = 6903573504541265696L;

}
