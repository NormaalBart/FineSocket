package me.bartvv.finesocket;

import java.nio.charset.StandardCharsets;

import com.google.common.hash.Hashing;

public class PasswordUtils {

	public static String hashString( String string ) {
		return Hashing.sha512().hashString( string, StandardCharsets.UTF_8 ).toString();
	}

}
