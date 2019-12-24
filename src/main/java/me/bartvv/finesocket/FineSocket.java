package me.bartvv.finesocket;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.security.KeyStore;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.java_websocket.server.DefaultSSLWebSocketServerFactory;

import com.google.common.collect.Maps;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import lombok.Getter;
import me.bartvv.finesocket.commands.Commandfinesocket;
import me.bartvv.finesocket.manager.FileManager;

@Getter
public class FineSocket extends JavaPlugin {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private FileManager config;
	private Map< String, String > passwords = Maps.newHashMap();
	private ChatServer chatServer;

	@SuppressWarnings( "serial" )
	@Override
	public void onEnable() {
		this.config = new FileManager( this, "config.yml", -1, getDataFolder(), false );
		Bukkit.getScheduler().runTaskAsynchronously( this, () -> {
			try {
				this.chatServer = new ChatServer( this.config.getInt( "websocket.port" ), this );
			} catch ( UnknownHostException e ) {
				e.printStackTrace();
			}

			if ( this.config.getBoolean( "websocket.ssl", true ) ) {
				try {
					// load up the key store
					String STORETYPE = "JKS";
					String KEYSTORE = "keystore.jks";
					String STOREPASSWORD = this.config.getString( "websocket.keystore.store-password" );
					String KEYPASSWORD = this.config.getString( "websocket.keystore.key-password" );

					File file = new File( getDataFolder(), KEYSTORE );
					if ( !file.exists() ) {
						file.createNewFile();
						try ( InputStream inputStream = getResource( "KeyStore.jks" );
								OutputStream outputStream = new FileOutputStream( file ) ) {
							file.createNewFile();

							byte[] buffer = new byte[ inputStream.available() ];
							inputStream.read( buffer );

							outputStream.write( buffer );
						} catch ( IOException e ) {
							e.printStackTrace();
						}
					}

					KeyStore keyStore = KeyStore.getInstance( STORETYPE );
					keyStore.load( new FileInputStream( file ), STOREPASSWORD.toCharArray() );

					KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance( "SunX509" );
					keyManagerFactory.init( keyStore, KEYPASSWORD.toCharArray() );
					TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance( "SunX509" );
					trustManagerFactory.init( keyStore );

					SSLContext sslContext = SSLContext.getInstance( "TLS" );
					sslContext.init( keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null );
					this.chatServer.setWebSocketFactory( new DefaultSSLWebSocketServerFactory( sslContext ) );
					getLogger().info( "SSL enabled when using WebSockets." );
				} catch ( Exception exc ) {
					exc.printStackTrace();
					getLogger().warning( "==============================================" );
					getLogger().warning( " " );
					getLogger().warning( "WebSocket is NOT secured with SSL, watch out !" );
					getLogger().warning( " " );
					getLogger().warning( "==============================================" );
				}
			} else {
				getLogger().warning( "==============================================" );
				getLogger().warning( " " );
				getLogger().warning( "WebSocket is NOT secured with SSL, watch out !" );
				getLogger().warning( " " );
				getLogger().warning( "==============================================" );
			}

			this.chatServer.start();
			while ( true ) {}
		} );

		getCommand( "finesocket" ).setExecutor( new Commandfinesocket( this ) );

		File file = new File( this.getDataFolder(), "passwords.json" );
		if ( file.exists() )
			try ( FileReader fileReader = new FileReader( file ) ) {
				this.passwords = GSON.fromJson( fileReader, new TypeToken< Map< String, String > >() {}.getType() );
			} catch ( Exception exc ) {
				exc.printStackTrace();
			}
		file = new File( this.getDataFolder(), "banned.json" );
		if ( file.exists() )
			try ( FileReader fileReader = new FileReader( file ) ) {
				this.chatServer.setBannedIPs(
						GSON.fromJson( fileReader, new TypeToken< Map< String, Long > >() {}.getType() ) );
			} catch ( Exception exc ) {
				exc.printStackTrace();
			}
		Bukkit.getScheduler().runTaskTimerAsynchronously( this, () -> save(), TimeUnit.HOURS.toSeconds( 12 ) * 20,
				TimeUnit.HOURS.toSeconds( 12 ) * 20 );
	}

	@Override
	public void onDisable() {
		try {
			this.chatServer.stop( 1000 );
		} catch ( InterruptedException e ) {
			e.printStackTrace();
		}
		save();
		Bukkit.getScheduler().cancelTasks( this );
	}

	private void save() {
		if ( !this.passwords.isEmpty() ) {
			File file = new File( this.getDataFolder(), "passwords.json" );
			try {
				file.createNewFile();
				FileChannel.open( file.toPath(), StandardOpenOption.WRITE )
						.write( ByteBuffer.wrap( GSON.toJson( this.passwords ).getBytes() ) );
			} catch ( IOException e ) {
				e.printStackTrace();
			}
		}
		if ( !this.chatServer.getBannedIPs().isEmpty() ) {
			File file = new File( this.getDataFolder(), "banned.json" );
			try {
				file.createNewFile();
				FileChannel.open( file.toPath(), StandardOpenOption.WRITE )
						.write( ByteBuffer.wrap( GSON.toJson( this.chatServer.getBannedIPs() ).getBytes() ) );
			} catch ( IOException e ) {
				e.printStackTrace();
			}
		}
	}
}