package com.namelessdev.mpdroid.fragments;

import android.content.DialogInterface;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.app.AlertDialog;
import android.widget.EditText;

import com.namelessdev.mpdroid.MPDApplication;
import com.namelessdev.mpdroid.R;
import com.namelessdev.mpdroid.library.ILibraryFragmentActivity;
import com.namelessdev.mpdroid.tools.Tools;

/**
 * Created by uwe on 28.12.16.
 */

import org.a0z.mpd.MPD;
import org.a0z.mpd.MPDCommand;
import org.a0z.mpd.MPDStatus;
import org.a0z.mpd.exception.MPDException;
import org.a0z.mpd.item.Album;
import org.a0z.mpd.item.Genre;
import org.a0z.mpd.item.Item;
import org.a0z.mpd.item.Artist;
import org.a0z.mpd.item.Music;
import org.a0z.mpd.item.PlaylistFile;
import org.a0z.mpd.item.Stream;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.TreeMap;


public abstract class BrowseFragmentNew
	extends Fragment
	implements MenuItem.OnMenuItemClickListener
{
	public static final int ADD = 0;
	public static final int ADD_REPLACE = 1;
	public static final int ADD_PLAY = 2;
	public static final int ADD_TO_PLAYLIST = 3;
	public static final int ADD_REPLACE_PLAY = 4;
	public static final int GOTO_ARTIST = 5;


	protected abstract String getTAG();

	public abstract String getTitle();
	public ILibraryFragmentActivity getMPDActivity()
	{ return (ILibraryFragmentActivity)getActivity(); }


	protected static final MPDApplication getApp() { return MPDApplication.getInstance(); }


	protected void onItemClick(final Item item)
	{
		generateItemGui(item).onItemClick();
	}

	public void onCreateContextMenu(final ContextMenu menu, final Item item, int listIndex)
	{
		ItemGui itemGui=generateItemGui(item);
		menu.setHeaderTitle(item.mainText());

		// If in simple mode, show "Play" (add, replace & play), "Add to queue" and "Add to playlist"
		if (getApp().isInSimpleMode())
		{
			menu.add(ADD_REPLACE_PLAY, listIndex, 0, R.string.play)
			    .setOnMenuItemClickListener(this);

			menu.add(ADD, listIndex, 0, R.string.addToQueue)
			    .setOnMenuItemClickListener(this);
		}
		else
		{
			menu.add(ADD, listIndex, 0, itemGui.addItemResId())
			    .setOnMenuItemClickListener(this);

			menu.add(ADD_REPLACE, listIndex, 0, R.string.addAndReplace)
			    .setOnMenuItemClickListener(this);

			menu.add(ADD_REPLACE_PLAY, listIndex, 0, R.string.addAndReplacePlay)
			    .setOnMenuItemClickListener(this);

			menu.add(ADD_PLAY, listIndex, 0, R.string.addAndPlay)
			    .setOnMenuItemClickListener(this);
		}

		if (itemGui.showContextMenuPlaylist() &&
			getApp().oMPDAsyncHelper.oMPD.isCommandAvailable(MPDCommand.MPD_CMD_LISTPLAYLISTS))
		{
			int id = 0;
			final SubMenu playlistMenu = menu.addSubMenu(R.string.addToPlaylist);
			playlistMenu.add(ADD_TO_PLAYLIST, listIndex, id++, R.string.newPlaylist)
			            .setOnMenuItemClickListener(this);

			try {
				final List<Item> playlists = getApp().oMPDAsyncHelper.oMPD.getPlaylists();

				if (null != playlists)
				{
					for (final Item pl : playlists)
					{
						playlistMenu.add(ADD_TO_PLAYLIST, listIndex, id++, pl.getName())
						            .setOnMenuItemClickListener(this);
					}
				}
			}
			catch (final IOException | MPDException e)
			{
				Log.e(getTAG(), "Failed to parse playlists.", e);
			}
		}

		if(itemGui.showContectMenuGotoArtist())
			menu.add(GOTO_ARTIST, listIndex, 0, R.string.goToArtist)
			    .setOnMenuItemClickListener(this);
	}

	private void addAndReplace(final ItemGui item, final boolean replace, final boolean play)
	{
		getApp().oMPDAsyncHelper.execAsync(
			()->
			{
				final MPDStatus status = getApp().oMPDAsyncHelper.oMPD.getStatus();

				// Let the user know if we're not going to play the added music.
				boolean p=play;
				if (play && status.isRandom() && status.isState(MPDStatus.STATE_PLAYING))
				{
					Tools.notifyUser(R.string.notPlayingInRandomMode);
					p=false;
				}
				item.addToQueue(replace, p);
			}
		);
	}

	private void addToPlaylist(ItemGui item, int order, String menuText)
	{
		//
		//final int id = menuItem.getOrder();
		if (order == 0)
		{
			final EditText input = new EditText(getActivity());
			new AlertDialog.Builder(getActivity())
				.setTitle(R.string.playlistName)
				.setMessage(R.string.newPlaylistPrompt)
				.setView(input)
				.setPositiveButton(android.R.string.ok,
				                   (final DialogInterface dialog, final int which)->
				                   {
					                   final String name = input.getText().toString().trim();
					                   if (!name.isEmpty())
					                   {
						                   getApp().oMPDAsyncHelper.execAsync(
							                   ()->
							                   {
								                   item.addToPlaylist(name);
							                   }
						                   );
					                   }
				                   })
				.setNegativeButton(android.R.string.cancel,
				                   (final DialogInterface dialog,final int which) -> {})
				.show();
		}
		else
		{

			item.addToPlaylist(menuText);
		}
	}

	public boolean onMenuItemClick(final Item item, int index, int order, String menuText)
	{
		ItemGui itemGui=generateItemGui(item);
		switch (index)
		{
			case ADD_REPLACE_PLAY:
				addAndReplace(itemGui, true, true); break;
			case ADD_REPLACE:
				addAndReplace(itemGui, true, false); break;
			case ADD:
				addAndReplace(itemGui, false, false); break;
			case ADD_PLAY:
				addAndReplace(itemGui, false, true); break;
			case ADD_TO_PLAYLIST:
				addToPlaylist(itemGui, order, menuText);
				break;
			case GOTO_ARTIST:
				Artist artist = itemGui.getArtist();
				if (artist != null)
					getMPDActivity().pushLibraryFragment(AlbumsFragment.createAlbumsFragment(artist));
				break;
			default:
				return false;
		}

		return true;
	}


	static private class ItemWrapper
	{
		public static boolean register(java.lang.Class rawItem, java.lang.Class guiItem)
		{
			generator.put(rawItem, guiItem);
			return true;
		}

		public static ItemGui generate(BrowseFragmentNew bf, Item item)
		{
			Class rawClass=item.getClass();
			Class guiClass=generator.get(rawClass);
			try
			{
				return (ItemGui) guiClass
					.getDeclaredConstructor(BrowseFragmentNew.class, rawClass)
				    .newInstance(bf, item);
			}
			catch(NoSuchMethodException| java.lang.InstantiationException |IllegalAccessException|InvocationTargetException e)
			{
				return null;
			}
		}

		static private TreeMap<Class, Class> generator=
			new TreeMap<>((Class c1, Class c2)->c1.getName().compareTo(c2.getName()));
	}
	private ItemGui generateItemGui(Item item)
	{
		return ItemWrapper.generate(this, item);
	}


	abstract class ItemGui
	{
		abstract public void onItemClick();
		public boolean showContextMenuPlaylist() { return true; }
		abstract public int addItemResId();
		public boolean showContectMenuGotoArtist() { return true; }
		public void addToQueue(final boolean replace, final boolean play) {}
		public void addToPlaylist(final String playlist) {}
		public Artist getArtist() { return null; }
	}

	class ArtistGui extends ItemGui
	{
		Artist artist;

		public ArtistGui(Artist a) { artist=a; }

		public void onItemClick()
		{
			Genre genre=null;
			final AlbumsFragment af=AlbumsFragment.createAlbumsFragment(artist, genre);
			getMPDActivity().pushLibraryFragment(af);
		}

		public int addItemResId() { return R.string.addArtist; }

		public boolean showContectMenuGotoArtist() { return false; }

		public void addToQueue(final boolean replace, final boolean play)
		{
			try
			{
				MPD mpd=getApp().oMPDAsyncHelper.oMPD;
				mpd.add(artist, replace, play);
				Tools.notifyUser(R.string.artistAdded, artist);
			}
			catch (final IOException | MPDException e)
			{
				Log.e(getTAG(), "Failed to add.", e);
			}
		}
		public void addToPlaylist(final String playlist)
		{
			try
			{
				getApp().oMPDAsyncHelper.oMPD.addToPlaylist(playlist, artist);
				if (isAdded()) {
					Tools.notifyUser(R.string.artistAdded, artist);
				}
			} catch (final IOException | MPDException e) {
				Log.e(getTAG(), "Failed to add to playlist.", e);
			}
		}
		public Artist getArtist() { return artist; }
	}
	static final boolean genArtistGui= ItemWrapper.register(Artist.class, ArtistGui.class);

	class AlbumGui extends ItemGui
	{
		Album album;

		public AlbumGui(Album a) { album=a; }

		public void onItemClick()
		{
			getMPDActivity().pushLibraryFragment(new SongsFragment().init(album));
		}

		public int addItemResId() { return R.string.addAlbum; }

		public void addToQueue(final boolean replace, final boolean play)
		{
			try
			{
				MPD mpd=getApp().oMPDAsyncHelper.oMPD;
				mpd.add(album, replace, play);
				Tools.notifyUser(R.string.albumAdded, album);
			}
			catch (final IOException | MPDException e)
			{
				Log.e(getTAG(), "Failed to add.", e);
			}
		}
		public void addToPlaylist(final String playlist)
		{
			try
			{
				getApp().oMPDAsyncHelper.oMPD.addToPlaylist(playlist, album);
				Tools.notifyUser(R.string.albumAdded, album);
			}
			catch (final IOException | MPDException e)
			{
				Log.e(getTAG(), "Failed to add.", e);
			}

		}
		public Artist getArtist() { return album.getArtist(); }
	}
	static final boolean genAlbumGui= ItemWrapper.register(Album.class, AlbumGui.class);

	class MusicGui extends ItemGui
	{
		Music music;

		public MusicGui(Music m) { music=m; }

		public void onItemClick()
		{
			try
			{
				boolean replace = false, play = false;
				getApp().oMPDAsyncHelper.oMPD.add(music, replace, play);

				Tools.notifyUser(R.string.songAdded, music.getTitle(), music.getName());
			} catch (final IOException | MPDException e)
			{
				Log.e(getTAG(), "Failed to add.", e);
			}
		}
		public int addItemResId() { return R.string.addSong; }

		public void addToQueue(final boolean replace, final boolean play)
		{
			try
			{
				MPD mpd=getApp().oMPDAsyncHelper.oMPD;
				mpd.add(music, replace, play);
				Tools.notifyUser(R.string.songAdded, music);
			}
			catch (final IOException | MPDException e)
			{
				Log.e(getTAG(), "Failed to add.", e);
			}
		}
		public void addToPlaylist(final String playlist)
		{
			try
			{
				getApp().oMPDAsyncHelper.oMPD.addToPlaylist(playlist, music);
				Tools.notifyUser(R.string.songAdded, music);
			}
			catch (final IOException | MPDException e)
			{
				Log.e(getTAG(), "Failed to add to playlist.", e);
			}
		}
		public Artist getArtist() {	return new Artist(music.getAlbumArtistOrArtist()); }
	}
	static final boolean genMusicGui= ItemWrapper.register(Music.class, MusicGui.class);



	class PlaylistFileGui extends ItemGui
	{
		PlaylistFile playlist;

		public PlaylistFileGui(PlaylistFile p) {playlist=p;}

		public void onItemClick()
		{
			// TODO
		}

		public boolean showContextMenuPlaylist() { return false;}
		public int addItemResId() { return -1; /* TODO: */  }
	}
	static final boolean genPlaylistGui=ItemWrapper.register(PlaylistFile.class, PlaylistFileGui.class);

	class StreamGui extends ItemGui
	{
		Stream stream;

		public void StreamGui(Stream s) {stream=s;}

		public void onItemClick()
		{
			// TODO
		}

		public boolean showContextMenuPlaylist() { return false;}
		public int addItemResId() { return -1; /* TODO: */  }
	}
	static final boolean genStreamGui=ItemWrapper.register(Stream.class, StreamGui.class);
}
