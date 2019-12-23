package me.bartvv.finesocket;

import java.lang.reflect.InvocationTargetException;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import lombok.Getter;
import lombok.Setter;

public class ChatServer extends WebSocketServer {

	private final FineSocket fineSocket;
	private static final Pattern PATTERN = Pattern.compile( "\\." );
	private List< WebSocket > authorizedConnections = Lists.newArrayList();
	private Map< String, Integer > passwordAttempts = Maps.newHashMap();
	@Getter
	@Setter
	private Map< String, Long > bannedIPs = Maps.newHashMap();

	public ChatServer( int port, FineSocket fineSocket ) throws UnknownHostException {
		super( new InetSocketAddress( port ) );
		this.fineSocket = fineSocket;
	}

	public ChatServer( InetSocketAddress address, FineSocket fineSocket ) {
		super( address );
		this.fineSocket = fineSocket;
	}

	@Override
	public void onOpen( WebSocket webSocket, ClientHandshake handshake ) {
		if ( this.bannedIPs.getOrDefault( webSocket.getRemoteSocketAddress().getHostName(),
				System.currentTimeMillis() - 10 ) > System.currentTimeMillis() ) {
			webSocket.close();
			return;
		}
		this.bannedIPs.remove( webSocket.getRemoteSocketAddress().getHostName() );
		Bukkit.getScheduler().runTaskLaterAsynchronously( this.fineSocket, () -> {
			if ( webSocket.isClosed() )
				return;
			if ( !this.authorizedConnections.contains( webSocket ) ) {
				int tries = this.passwordAttempts.put( webSocket.getRemoteSocketAddress().getHostName(),
						this.passwordAttempts.getOrDefault( webSocket.getRemoteSocketAddress().getHostName(), 0 ) + 1 );
				if ( tries >= this.fineSocket.getConfig().getInt( "websocket.maxtries" ) ) {
					this.bannedIPs.put( webSocket.getRemoteSocketAddress().getHostName(), System.currentTimeMillis()
							+ TimeUnit.SECONDS.toMillis( this.fineSocket.getConfig().getInt( "websocket.tempban" ) ) );
					this.passwordAttempts.remove( webSocket.getRemoteSocketAddress().getHostName() );
				}
				if ( !webSocket.isClosed() )
					webSocket.close();
			}
		}, this.fineSocket.getConfig().getInt( "websocket.disconnectTime" ) * 20 );
	}

	@Override
	public void onClose( WebSocket conn, int code, String reason, boolean remote ) {
		this.authorizedConnections.remove( conn );
	}

	@Override
	public void onMessage( WebSocket conn, String message ) {
		onMessageReceive( conn, message );
	}

	@Override
	public void onMessage( WebSocket conn, ByteBuffer message ) {
		onMessageReceive( conn, message.toString() );
	}

	private void onMessageReceive( WebSocket webSocket, String message ) {
		if ( !this.authorizedConnections.contains( webSocket ) && !message.startsWith( "login." ) ) {
			Integer tries = this.passwordAttempts.put( webSocket.getRemoteSocketAddress().getHostName(),
					this.passwordAttempts.getOrDefault( webSocket.getRemoteSocketAddress().getHostName(), 0 ) + 1 );
			if ( tries == null )
				tries = 1;
			if ( tries >= this.fineSocket.getConfig().getInt( "websocket.maxtries" ) ) {
				this.bannedIPs.put( webSocket.getRemoteSocketAddress().getHostName(), System.currentTimeMillis()
						+ TimeUnit.SECONDS.toMillis( this.fineSocket.getConfig().getInt( "websocket.tempban" ) ) );
				this.passwordAttempts.remove( webSocket.getRemoteSocketAddress().getHostName() );
			}
			webSocket.close();
			return;
		}
		if ( message.startsWith( "login." ) ) {
			String[] args = PATTERN.split( message );
			if ( args.length != 3 ) {
				webSocket.close();
				return;
			}
			String username = args[ 1 ];
			String password = PasswordUtils.hashString( args[ 2 ] );
			if ( !this.fineSocket.getPasswords().getOrDefault( username, "N/A" ).equalsIgnoreCase( password ) ) {
				int tries = this.passwordAttempts.put( webSocket.getRemoteSocketAddress().getHostName(),
						this.passwordAttempts.getOrDefault( webSocket.getRemoteSocketAddress().getHostName(), 0 ) + 1 );
				if ( tries >= this.fineSocket.getConfig().getInt( "websocket.maxtries" ) ) {
					this.bannedIPs.put( webSocket.getRemoteSocketAddress().getHostName(), System.currentTimeMillis()
							+ TimeUnit.SECONDS.toMillis( this.fineSocket.getConfig().getInt( "websocket.tempban" ) ) );
					this.passwordAttempts.remove( webSocket.getRemoteSocketAddress().getHostName() );
				}
				webSocket.close();
				return;
			}
			this.passwordAttempts.remove( webSocket.getRemoteSocketAddress().getHostName() );
			webSocket.send( "{\"id\":\"login\",\"success\":true}" );
			this.authorizedConnections.add( webSocket );
			return;
		}
		try {
			String response = StringParser.parseString( message );
			webSocket.send( response );
		} catch ( ClassNotFoundException | IllegalAccessException | IllegalArgumentException
				| InvocationTargetException e ) {
			e.printStackTrace();
			webSocket.send( "{\"error\":\"" + e.getMessage() + "\"}" );
		}
		return;
	}

	@Override
	public void onError( WebSocket conn, Exception ex ) {
		ex.printStackTrace();
		if ( conn != null ) {
			// some errors like port binding failed may not be assignable to a specific
			// websocket
		}
	}

	@Override
	public void onStart() {
		this.fineSocket.getLogger().info( "WebSocket server started !" );
		setConnectionLostTimeout( 0 );
		setConnectionLostTimeout( 100 );
	}
}