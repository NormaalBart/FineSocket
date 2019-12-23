package me.bartvv.finesocket;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Material;

import com.google.common.base.Enums;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import me.bartvv.finesocket.exceptions.NullReturnException;

public class StringParser {

	private static final Map< Class< ? >, Converter< ? > > CONVERTERS = Maps.newHashMap();
	private static final Pattern COLON_PATTERN = Pattern.compile( "[:]+(?![^(]*\\))" );
	private static final Pattern PARAMETER_PATTERN = Pattern.compile( "\"(.*?)\"" );
	private static final Pattern MULTI_PATTERN = Pattern.compile( "[,]+(?![^(]*\\))" );
	private static final Cache< String, Set< Method > > METHOD_CACHE = CacheBuilder.newBuilder()
			.expireAfterAccess( 2, TimeUnit.HOURS ).build();
	private static final Gson GSON = new GsonBuilder().create();

	static {
		CONVERTERS.put( Boolean.class, Boolean::valueOf );
		CONVERTERS.put( boolean.class, Boolean::parseBoolean );
		CONVERTERS.put( Byte.class, Byte::valueOf );
		CONVERTERS.put( byte.class, Byte::parseByte );
		CONVERTERS.put( Character.class, string -> string.charAt( 0 ) );
		CONVERTERS.put( char.class, string -> string.charAt( 0 ) );
		CONVERTERS.put( Short.class, Short::valueOf );
		CONVERTERS.put( short.class, Short::parseShort );
		CONVERTERS.put( Integer.class, Integer::valueOf );
		CONVERTERS.put( int.class, Integer::parseInt );
		CONVERTERS.put( Long.class, Long::valueOf );
		CONVERTERS.put( long.class, Long::parseLong );
		CONVERTERS.put( Float.class, Float::valueOf );
		CONVERTERS.put( float.class, Float::parseFloat );
		CONVERTERS.put( Double.class, Double::valueOf );
		CONVERTERS.put( double.class, Double::parseDouble );
		CONVERTERS.put( String.class, string -> {
			if ( string == null || string.equalsIgnoreCase( "null" ) )
				return null;
			return string;
		} );
		CONVERTERS.put( Material.class, Material::matchMaterial );
		CONVERTERS.put( UUID.class, UUID::fromString );
	}

	public static String parseString( String string )
			throws ClassNotFoundException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		String id = string.substring( 0, string.indexOf( '.' ) );
		String javaCode = string.substring( string.indexOf( '.' ) + 1 );
		Object object;
		try {
			object = parseString( null, javaCode );
		} catch ( NullReturnException | NoSuchMethodException e ) {
			object = e.getMessage();
		}
		if ( !( object instanceof List ) )
			object = Lists.newArrayList( object );
		Map< String, Object > map = Maps.newLinkedHashMap();
		map.put( "id", id );
		map.put( "data", object );
		return GSON.toJson( map );
	}

	@SuppressWarnings( { "unchecked", "rawtypes" } )
	private static Object parseString( Object object, String string )
			throws ClassNotFoundException, IllegalAccessException, IllegalArgumentException, InvocationTargetException,
			NullReturnException, NoSuchMethodException {
		String[] args = COLON_PATTERN.split( string );
		String clazzName = args[ 0 ];
		Class< ? > clazz = object == null ? Class.forName( clazzName ) : null;
		argumentLoop: for ( int i = 1; i < args.length; i++ ) {
			String methodString = args[ i ];
			int index = methodString.indexOf( '(' );
			if ( methodString.charAt( index + 1 ) == ')' ) {
				index = -1;
				methodString = methodString.substring( 0, methodString.length() - 2 );
			}
			if ( index == -1 ) {
				for ( Method method : getMethods( methodString, clazz == null ? object.getClass() : clazz, 0 ) ) {
					object = method.invoke( object );
					if ( object == null ) {
						throw new NullReturnException( "method " + methodString + " is returning null." );
					}
					clazz = null;
					continue argumentLoop;
				}
				throw new NoSuchMethodException( "method " + methodString + " does not exist" );
			}

			if ( methodString.toLowerCase().startsWith( "multi" ) ) {
				methodString = methodString.substring( 6, methodString.length() - 1 );
				List< Object > returnArguments = Lists.newArrayList();
				String[] multiMethods = MULTI_PATTERN.split( methodString );
				for ( String multiArg : multiMethods ) {
					while ( multiArg.startsWith( " " ) )
						multiArg = multiArg.substring( 1 );
					if ( object == null )
						multiArg = clazzName + ":" + multiArg;
					Object object2;
					try {
						object2 = parseString( object == null ? null : object, multiArg );
					} catch ( NullReturnException | NoSuchMethodException e ) {
						object2 = e.getMessage();
					}
					returnArguments.add( object2 );
				}
				return returnArguments;
			}

			Matcher matcher = PARAMETER_PATTERN
					.matcher( methodString.substring( index + 1, methodString.length() - 1 ) );
			List< String > list = Lists.newArrayList();
			while ( matcher.find() )
				list.add( matcher.group() );
			methodString = methodString.substring( 0, index );
			methodLoop: for ( Method method : getMethods( methodString, clazz == null ? object.getClass() : clazz,
					list.size() ) ) {
				Object[] objectParameters = new Object[ method.getParameterCount() ];
				for ( int j = 0; j < method.getParameterCount(); j++ ) {
					try {
						String parameter = list.get( j );
						parameter = parameter.substring( 1, parameter.length() - 1 );
						Class< ? > parameterClazz = method.getParameterTypes()[ j ];
						if ( parameterClazz.isEnum() )
							objectParameters[ j ] = Enums
									.getIfPresent( ( Class< ? extends Enum > ) parameterClazz, parameter ).orNull();
						else
							objectParameters[ j ] = CONVERTERS.get( parameterClazz ).tryConvert( parameter );
					} catch ( Exception exc ) {
						continue methodLoop;
					}
				}
				object = method.invoke( object, objectParameters );
				if ( object == null )
					throw new NullReturnException( "method " + methodString + " is returning null." );
				clazz = null;
				continue argumentLoop;
			}
			throw new NoSuchMethodException( "method " + methodString + " does not exist" );
		}
		return object;
	}

	public static < T extends Enum< T > > T getEnumIfPresent( Class< T > enumClass, String value ) {
		return Enums.getIfPresent( enumClass, value ).orNull();
	}

	private static Set< Method > getMethods( String methodName, Class< ? > clazz, int parameterCount ) {
		try {
			return METHOD_CACHE.get( clazz.getName() + "." + methodName, () -> {
				Set< Method > methods = Sets.newHashSet();
				for ( Method method : clazz.getMethods() )
					if ( method.getName().equalsIgnoreCase( methodName )
							&& method.getParameterCount() == parameterCount )
						methods.add( method );
				for ( Method method : clazz.getDeclaredMethods() )
					if ( method.getName().equalsIgnoreCase( methodName )
							&& method.getParameterCount() == parameterCount ) {
						method.setAccessible( true );
						methods.add( method );
					}
				return methods;
			} );
		} catch ( ExecutionException e ) {
			e.printStackTrace();
		}
		return null;
	}
}
