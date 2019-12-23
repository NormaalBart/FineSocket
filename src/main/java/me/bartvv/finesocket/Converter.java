package me.bartvv.finesocket;

public interface Converter< T > {

	public T tryConvert( String string ) throws Exception;

}
