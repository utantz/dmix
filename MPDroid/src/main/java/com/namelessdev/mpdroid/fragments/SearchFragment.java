package com.namelessdev.mpdroid.fragments;

import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import android.widget.AdapterView.AdapterContextMenuInfo;

import com.namelessdev.mpdroid.R;
import com.namelessdev.mpdroid.adapters.SeparatedListAdapter;
import com.namelessdev.mpdroid.views.SearchResultDataBinder;

import org.a0z.mpd.exception.MPDException;
import org.a0z.mpd.item.Album;
import org.a0z.mpd.item.Artist;
import org.a0z.mpd.item.Item;
import org.a0z.mpd.item.Music;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SearchFragment extends BrowseFragmentNew implements AdapterView.OnItemClickListener
{
	// the fragment initialization parameters
	protected final String getTAG() { return "SearchFragment"; }

	private static final String ARG_QUERY = "query";
	private static final String ARG_TAB_INIT = "tab_init";

	private String mSearchKeywords = null;
	private int mInitCurrentTab =0;

	private final ArrayList<Album> mAlbumResults=new ArrayList<>();
	private final ArrayList<Artist> mArtistResults=new ArrayList<>();
	private final ArrayList<Music> mSongResults=new ArrayList<>();

	TabHost mTabHost;
	TextView mTabArtists, mTabAlbums, mTabSongs;
	ListView mListArtists, mListAlbums, mListSongs;

	View mLoadingView;

	public SearchFragment()	{}

	public SearchFragment (String query)
	{
		mSearchKeywords=query;
		setArguments(saveArguments(new Bundle()));
	}

	private Bundle saveArguments(Bundle b)
	{
		b.putString(ARG_QUERY, mSearchKeywords);
		b.putInt(ARG_TAB_INIT, mInitCurrentTab);
		return b;
	}

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		Bundle b=savedInstanceState!=null
			? savedInstanceState
			: getArguments();

		mSearchKeywords = b.getString(ARG_QUERY);
		mInitCurrentTab = b.getInt(ARG_TAB_INIT);
	}

	public void onSaveInstanceState(Bundle outState)
	{
		saveArguments(outState);
		super.onSaveInstanceState(outState);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
	                         Bundle savedInstanceState)
	{
		View v=inflater.inflate(R.layout.search_fragment, container, false);

		mTabHost=(TabHost)v.findViewById(android.R.id.tabhost);
		mTabHost.setup();

		mTabHost.addTab(mTabHost.newTabSpec(null)
		                        .setContent(R.id.tabArtists)
		                        .setIndicator(getString(R.string.artists)));

		mTabHost.addTab(mTabHost.newTabSpec(null)
		                        .setContent(R.id.tabAlbums)
		                        .setIndicator(getString(R.string.albums)));

		mTabHost.addTab(mTabHost.newTabSpec(null)
		                        .setContent(R.id.tabSongs)
		                        .setIndicator(getString(R.string.songs)));

		{
			TabWidget tw=mTabHost.getTabWidget();
			LinearLayout tabGroup;
			tabGroup = (LinearLayout) tw.getChildTabViewAt(0);
			mTabArtists = (TextView)tabGroup.findViewById(android.R.id.title);
			tabGroup = (LinearLayout) tw.getChildTabViewAt(1);
			mTabAlbums = (TextView)tabGroup.findViewById(android.R.id.title);
			tabGroup = (LinearLayout) tw.getChildTabViewAt(2);
			mTabSongs = (TextView)tabGroup.findViewById(android.R.id.title);
		}

		{
			View group;
			group = v.findViewById(R.id.tabArtists);
			mListArtists = (ListView)group.findViewById(android.R.id.list);
			mListArtists.setEmptyView(group.findViewById(android.R.id.empty));
			mListArtists.setOnItemClickListener(this);

			group = v.findViewById(R.id.tabAlbums);
			mListAlbums = (ListView)group.findViewById(android.R.id.list);
			mListAlbums.setEmptyView(group.findViewById(android.R.id.empty));
			mListAlbums.setOnItemClickListener(this);

			group = v.findViewById(R.id.tabSongs);
			mListSongs = (ListView)group.findViewById(android.R.id.list);
			mListSongs.setEmptyView(group.findViewById(android.R.id.empty));
			mListSongs.setOnItemClickListener(this);
		}

		registerForContextMenu(mListArtists);
		registerForContextMenu(mListAlbums);
		registerForContextMenu(mListSongs);

		mLoadingView=v.findViewById(R.id.loadingLayout);

		mTabHost.setVisibility(View.GONE);
		mLoadingView.setVisibility(View.VISIBLE);

		getApp().oMPDAsyncHelper.execAsync(
			()->asyncOnCreateView(),
			()->asyncExecSucceeded()
		);

		return v;
	}

	public void onDestroyView()
	{
		mInitCurrentTab=mTabHost.getCurrentTab();
		super.onDestroyView();
	}

	public String getTitle()
	{
		return getApp().getString(R.string.search_for, mSearchKeywords);
	}


	protected void asyncOnCreateView() {
		final String finalSearch = mSearchKeywords.toLowerCase();

		List<Music> arrayMusic = null;

		try {
			arrayMusic = getApp().oMPDAsyncHelper.oMPD.search("any", finalSearch);
		} catch (final IOException | MPDException e) {
			Log.e(getTAG(), "MPD search failure.", e);

		}

		if (arrayMusic == null) {
			return;
		}

		mArtistResults.clear();
		mAlbumResults.clear();
		mSongResults.clear();

		String tmpValue;
		boolean valueFound;
		for (final Music music : arrayMusic) {
			if (music.getTitle() != null && music.getTitle().toLowerCase().contains(finalSearch)) {
				mSongResults.add(music);
			}
			valueFound = false;
			Artist artist = music.getAlbumArtistAsArtist();
			if (artist == null || artist.isUnknown()) {
				artist = music.getArtistAsArtist();
			}
			if (artist != null) {
				final String name = artist.getName();
				if (name != null) {
					tmpValue = name.toLowerCase();
					if (tmpValue.contains(finalSearch)) {
						for (final Artist artistItem : mArtistResults) {
							final String artistItemName = artistItem.getName();
							if (artistItemName != null &&
								artistItemName.equalsIgnoreCase(tmpValue)) {
								valueFound = true;
							}
						}
						if (!valueFound) {
							mArtistResults.add(artist);
						}
					}
				}
			}

			valueFound = false;
			final Album album = music.getAlbumAsAlbum();
			if (album != null) {
				final String albumName = album.getName();
				if (albumName != null) {
					tmpValue = albumName.toLowerCase();
					if (tmpValue.contains(finalSearch)) {
						for (final Album albumItem : mAlbumResults) {
							final String albumItemName = albumItem.getName();
							if (albumItemName.equalsIgnoreCase(tmpValue)) {
								valueFound = true;
							}
						}
						if (!valueFound) {
							mAlbumResults.add(album);
						}
					}
				}
			}
		}

		Collections.sort(mArtistResults);
		Collections.sort(mAlbumResults);
		Collections.sort(mSongResults, Music.COMPARE_WITHOUT_TRACK_NUMBER);
	}

	public void asyncExecSucceeded()
	{
		mTabArtists.setText(getString(R.string.artists) + " (" + mArtistResults.size() + ')');
		mTabAlbums.setText(getString(R.string.albums) + " (" + mAlbumResults.size() + ')');
		mTabSongs.setText(getString(R.string.songs) + " (" + mSongResults.size() + ')');

		// connect data containers with list views
		ListAdapter separatedListAdapter;
		separatedListAdapter =
			new SeparatedListAdapter(getActivity(),
			                         R.layout.search_list_item,
			                         new SearchResultDataBinder(),
			                         mArtistResults);
		mListArtists.setAdapter(separatedListAdapter);

		separatedListAdapter =
			new SeparatedListAdapter(getActivity(),
			                         R.layout.search_list_item,
			                         new SearchResultDataBinder(),
			                         mAlbumResults);
		mListAlbums.setAdapter(separatedListAdapter);

		separatedListAdapter =
			new SeparatedListAdapter(getActivity(),
			                         R.layout.search_list_item,
			                         new SearchResultDataBinder(),
			                         mSongResults);
		mListSongs.setAdapter(separatedListAdapter);

		mTabHost.setVisibility(View.VISIBLE);
		mLoadingView.setVisibility(View.GONE);

		mTabHost.setCurrentTab(mInitCurrentTab);
	}



	@Override
	public void onItemClick(final AdapterView<?> parent, final View view, final int position,
	                        final long id)
	{
		onItemClick((Item)parent.getAdapter().getItem(position));
	}

	protected Item getCurrentItem(int listIndex)
	{
		ListView l;
		switch(mTabHost.getCurrentTab())
		{
			case 0: l=mListArtists; break;
			case 1: l=mListAlbums; break;
			default: l=mListSongs;
		}

		return (Item)l.getItemAtPosition(listIndex);
	}


	public void onCreateContextMenu(final ContextMenu menu, final View v,
	                                final ContextMenu.ContextMenuInfo menuInfo)
	{
		AdapterContextMenuInfo info=(AdapterContextMenuInfo)menuInfo;
		int listIndex=(int)info.id;
		Item item=getCurrentItem(listIndex);
		onCreateContextMenu(menu, item, listIndex);
	}


	@Override
	public boolean onMenuItemClick(final MenuItem menuItem)
	{
		int index=menuItem.getGroupId();
		int order=menuItem.getOrder();
		String menuText=menuItem.getTitle().toString();
		Item item=getCurrentItem(menuItem.getItemId());
		return onMenuItemClick(item, index, order, menuText);
	}
}
