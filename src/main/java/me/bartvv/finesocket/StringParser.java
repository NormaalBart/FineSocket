package me.bartvv.finesocket;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.Material;

import com.google.common.base.Enums;
import com.google.common.base.Optional;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class StringParser {

	private static final Map< Class< ? >, Converter< ? > > CONVERTERS = Maps.newHashMap();
	private static final Pattern COLON_PATTERN = Pattern.compile( "[:]+(?![^(]*\\))" );
	private static final Pattern PARAMETER_PATTERN = Pattern.compile( "\"(.*?)\"" );
	private static final Pattern MULTI_PATTERN = Pattern.compile( "[,]+(?![^(]*\\))" );
	private static final Cache< String, Set< Method > > METHOD_CACHE = CacheBuilder.newBuilder()
			.expireAfterAccess( 2, TimeUnit.HOURS ).build();
	private static final Gson GSON = new GsonBuilder().serializeNulls().create();
	private static final ExecutorService EXECUTOR_SERVICE = Executors.newCachedThreadPool();
	private static final FineSocket FINE_SOCKET = FineSocket.getPlugin( FineSocket.class );

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

	public static void parseString( String string, Callback< String > callback )
			throws ClassNotFoundException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		String id = string.substring( 0, string.indexOf( '.' ) );
		String javaCode = string.substring( string.indexOf( '.' ) + 1 );
		List< String > returnArguments;
		try {
			returnArguments = EXECUTOR_SERVICE.submit( new Callable< List< String > >() {

				@SuppressWarnings( "unchecked" )
				@Override
				public List< String > call() throws Exception {
					Object object = parseString( null, javaCode );
					if ( !( object instanceof List ) )
						object = Lists.newArrayList( object );
					return ( List< String > ) object;
				}

			} ).get();
		} catch ( InterruptedException | ExecutionException e ) {
			callback.onSuccess(
					GSON.toJson( ImmutableMap.of( "id", id, "data", Lists.newArrayList( e.getStackTrace() ) ) ) );
			return;
		}

		String gson;
		try {
			gson = EXECUTOR_SERVICE.submit( new Callable< String >() {
				public String call() throws Exception {
					return GSON.toJson( ImmutableMap.of( "id", id, "data", returnArguments ) );
				}
			} ).get();
		} catch ( InterruptedException | ExecutionException e ) {
			callback.onSuccess(
					GSON.toJson( ImmutableMap.of( "id", id, "data", Lists.newArrayList( e.getStackTrace() ) ) ) );
			return;
		}
		callback.onSuccess( gson );
	}

	@SuppressWarnings( { "unchecked", "rawtypes" } )
	private static Object parseString( Object object, String string ) {
		String[] args = COLON_PATTERN.split( string );
		String clazzName = args[ 0 ];
		Class< ? > clazz;
		try {
			clazz = object == null ? Class.forName( clazzName ) : null;
		} catch ( ClassNotFoundException e ) {
			return "class not found: " + clazzName;
		}
		argumentLoop: for ( int i = clazz == null ? 0 : 1; i < args.length; i++ ) {
			String methodString = args[ i ];
			int index = methodString.indexOf( '(' );
			if ( methodString.charAt( index + 1 ) == ')' ) {
				index = -1;
				methodString = methodString.substring( 0, methodString.length() - 2 );
			}
			if ( index == -1 ) {
				if ( object == null ? clazz.isEnum() : false ) {
					Optional< ? extends Enum > optional = Enums.getIfPresent(
							( Class< ? extends Enum > ) ( clazz == null ? object.getClass() : clazz ), methodString );
					if ( optional.isPresent() ) {
						object = optional.get();
						continue argumentLoop;
					}
				}
				Optional< Method > optional = getMethod( methodString, clazz == null ? object.getClass() : clazz );
				if ( !optional.isPresent() )
					return "method " + methodString + " does not exist";
				try {
					Object object2 = object;
					object = Bukkit.getScheduler().callSyncMethod( FINE_SOCKET, new Callable< Object >() {
						@Override
						public Object call() throws Exception {
							return optional.get().invoke( object2 );
						}
					} ).get();
					continue argumentLoop;
				} catch ( InterruptedException | ExecutionException e ) {
					return e.getMessage() + "( error exeucting method: " + optional.get().getName() + " )";
				}
			}

			if ( methodString.toLowerCase().startsWith( "multi" ) ) {
				methodString = methodString.substring( 6, methodString.length() - 1 );
				List< String > returnArguments = Lists.newArrayList();
				String[] multiMethods = MULTI_PATTERN.split( methodString );
				for ( String multiArg : multiMethods ) {
					while ( multiArg.startsWith( " " ) )
						multiArg = multiArg.substring( 1 );
					if ( object == null )
						multiArg = clazzName + ":" + multiArg;
					returnArguments.add( parseString( object == null ? null : object, multiArg ).toString() );
				}
				return returnArguments;
			}

			if ( methodString.toLowerCase().startsWith( "filter" )
					&& ( object instanceof Iterable< ? > || object.getClass().isArray() ) ) {
				methodString = methodString.substring( 7, methodString.length() - 1 );
				List< Object > list = Lists.newArrayList();
				if ( object.getClass().isArray() )
					for ( Object obj : ( Object[] ) object ) {
						Object booleanObject = parseString( obj, methodString );
						if ( booleanObject == null )
							continue;
						boolean filtered = Boolean.valueOf( booleanObject.toString() );
						if ( filtered )
							list.add( obj );
					}
				else
					for ( Object obj : ( Iterable< ? > ) object ) {
						Object booleanObject = parseString( obj, methodString );
						if ( booleanObject == null )
							continue;
						boolean filtered = Boolean.valueOf( booleanObject.toString() );
						if ( filtered )
							list.add( obj );
					}
				object = list;
				continue argumentLoop;
			}

			if ( methodString.toLowerCase().startsWith( "foreach" )
					&& ( object instanceof Iterable< ? > || object.getClass().isArray() ) ) {
				methodString = methodString.substring( 8, methodString.length() - 1 );
				List< String > returnArguments = Lists.newArrayList();
				if ( object.getClass().isArray() )
					for ( Object obj : ( Object[] ) object )
						returnArguments.add( parseString( obj, methodString ).toString() );
				else
					for ( Object obj : ( Iterable< ? > ) object )
						returnArguments.add( parseString( obj, methodString ).toString() );
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
						if ( parameter.startsWith( "java:" ) ) {
							Object returnArgument = parseString( null, parameter.substring( 5 ) );
							objectParameters[ j ] = returnArgument;
						} else {
							Class< ? > parameterClazz = method.getParameterTypes()[ j ];
							if ( parameterClazz.isEnum() )
								objectParameters[ j ] = Enums
										.getIfPresent( ( Class< ? extends Enum > ) parameterClazz, parameter ).orNull();
							else
								objectParameters[ j ] = CONVERTERS.get( parameterClazz ).tryConvert( parameter );
						}
					} catch ( Exception exc ) {
						continue methodLoop;
					}
				}
				try {
					Object object2 = object;
					object = Bukkit.getScheduler().callSyncMethod( FINE_SOCKET, new Callable< Object >() {
						public Object call() throws Exception {
							return method.invoke( object2, objectParameters );
						}
					} ).get();
				} catch ( InterruptedException | ExecutionException e ) {
					return "error executing method: " + method.getName();
				}
				if ( object == null )
					return "method " + methodString + " is returning null.";
				clazz = null;
				continue argumentLoop;
			}
			return "method " + methodString + " does not exist";
		}
		return object;
	}

	private static Optional< Method > getMethod( String methodString, Class< ? > clazz ) {
		Set< Method > methods = getMethods( methodString, clazz, 0 );
		if ( methods.isEmpty() )
			return Optional.absent();
		else
			for ( Method method : methods )
				return Optional.of( method );
		
		return Optional.absent();
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
