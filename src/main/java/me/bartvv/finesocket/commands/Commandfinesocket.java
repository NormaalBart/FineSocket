package me.bartvv.finesocket.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import lombok.RequiredArgsConstructor;
import me.bartvv.finesocket.FineSocket;
import me.bartvv.finesocket.PasswordUtils;

@RequiredArgsConstructor
public class Commandfinesocket implements CommandExecutor {

	private final FineSocket fineSocket;

	@Override
	public boolean onCommand( CommandSender sender, Command command, String label, String[] args ) {
		if ( !sender.hasPermission( "finesocket.finesocket" ) ) {
			sender.sendMessage( this.fineSocket.getConfig().getString( "messages.no-permission" ) );
			return true;
		}
		if ( args.length == 0 ) {
			this.fineSocket.getConfig().getStringList( "messages.wrong-usage" ).forEach( sender::sendMessage );
			return true;
		}

		if ( args[ 0 ].equalsIgnoreCase( "create" ) && args.length == 3 ) {
			String username = args[ 1 ];
			String password = PasswordUtils.hashString( args[ 2 ] );
			if ( this.fineSocket.getPasswords().containsKey( username ) ) {
				sender.sendMessage( this.fineSocket.getConfig().getString( "messages.username-exists" ) );
				return true;
			}
			this.fineSocket.getPasswords().put( username, password );
			sender.sendMessage( this.fineSocket.getConfig().getString( "messages.created" ) );
			return true;
		} else if ( args[ 0 ].equalsIgnoreCase( "delete" ) && args.length == 2 ) {
			String username = args[ 1 ];
			if ( !this.fineSocket.getPasswords().containsKey( username ) ) {
				sender.sendMessage( this.fineSocket.getConfig().getString( "messages.no-username" ) );
				return true;
			}
			this.fineSocket.getPasswords().remove( username );
			sender.sendMessage( this.fineSocket.getConfig().getString( "messages.deleted" ) );
			return true;
		} else if ( args[ 0 ].equalsIgnoreCase( "edit" ) && args.length == 3 ) {
			String username = args[ 1 ];
			String password = PasswordUtils.hashString( args[ 2 ] );
			if ( !this.fineSocket.getPasswords().containsKey( username ) ) {
				sender.sendMessage( this.fineSocket.getConfig().getString( "messages.no-username" ) );
				return true;
			}
			this.fineSocket.getPasswords().put( username, password );
			sender.sendMessage( this.fineSocket.getConfig().getString( "messages.editted" ) );
			return true;
		}
		this.fineSocket.getConfig().getStringList( "messages.wrong-usage" ).forEach( sender::sendMessage );
		return true;
	}

}
