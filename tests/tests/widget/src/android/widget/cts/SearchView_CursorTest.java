/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.widget.cts;

import android.app.Activity;
import android.app.Instrumentation;
import android.cts.util.PollingCheck;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.provider.BaseColumns;
import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.MediumTest;
import android.text.TextUtils;
import android.widget.CursorAdapter;
import android.widget.SearchView;
import android.widget.SimpleCursorAdapter;
import android.widget.cts.util.ViewTestUtils;

import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Test {@link SearchView} with {@link Cursor}-backed suggestions adapter.
 */
@MediumTest
public class SearchView_CursorTest extends ActivityInstrumentationTestCase2<SearchViewCtsActivity> {
    private Instrumentation mInstrumentation;
    private Activity mActivity;
    private SearchView mSearchView;

    private static final String TEXT_COLUMN_NAME = "text";
    private String[] mTextContent;

    private CursorAdapter mSuggestionsAdapter;

    protected class MyQueryTextListener implements SearchView.OnQueryTextListener {
        @Override
        public boolean onQueryTextSubmit(String s) {
            return false;
        }

        @Override
        public boolean onQueryTextChange(String s) {
            if (mSuggestionsAdapter == null) {
                return false;
            }

            final MatrixCursor c = new MatrixCursor(
                    new String[] { BaseColumns._ID, TEXT_COLUMN_NAME} );
            for (int i = 0; i < mTextContent.length; i++) {
                if (mTextContent[i].toLowerCase().startsWith(s.toLowerCase())) {
                    c.addRow(new Object[]{i, mTextContent[i]});
                }
            }
            mSuggestionsAdapter.swapCursor(c);
            return false;
        }
    }

    protected class MySuggestionListener implements SearchView.OnSuggestionListener {
        @Override
        public boolean onSuggestionSelect(int position) {
            return false;
        }

        @Override
        public boolean onSuggestionClick(int position) {
            if (mSuggestionsAdapter != null) {
                final Cursor cursor = mSuggestionsAdapter.getCursor();
                if (cursor != null) {
                    cursor.moveToPosition(position);
                    mSearchView.setQuery(cursor.getString(1), false);
                }
            }
            return false;
        }
    }

    public SearchView_CursorTest() {
        super("android.widget.cts", SearchViewCtsActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mInstrumentation = getInstrumentation();
        mActivity = getActivity();
        mSearchView = (SearchView) mActivity.findViewById(R.id.search_view);

        // Local test data for the tests
        mTextContent = new String[] { "Akon", "Bono", "Ciara", "Dido", "Diplo" };

        mInstrumentation.runOnMainSync(() -> {
            // Use an adapter with our custom layout for each entry. The adapter "maps"
            // the content of the text column of our cursor to the @id/text1 view in the
            // layout.
            mSuggestionsAdapter = new SimpleCursorAdapter(
                    mActivity,
                    R.layout.searchview_suggestion_item,
                    null,
                    new String[] { TEXT_COLUMN_NAME },
                    new int[] { android.R.id.text1 },
                    CursorAdapter.FLAG_REGISTER_CONTENT_OBSERVER);
            mSearchView.setSuggestionsAdapter(mSuggestionsAdapter);
        });
    }

    public void testSuggestionFiltering() {
        final SearchView.OnQueryTextListener mockQueryTextListener =
                spy(new MyQueryTextListener());
        when(mockQueryTextListener.onQueryTextChange(anyString())).thenCallRealMethod();

        mInstrumentation.runOnMainSync(() -> {
                    mSearchView.setIconifiedByDefault(false);
                    mSearchView.setOnQueryTextListener(mockQueryTextListener);
                    mSearchView.requestFocus();
                });

        assertTrue(mSearchView.hasFocus());
        assertEquals(mSuggestionsAdapter, mSearchView.getSuggestionsAdapter());

        mInstrumentation.runOnMainSync(() -> mSearchView.setQuery("Bon", false));
        verify(mockQueryTextListener, times(1)).onQueryTextChange("Bon");

        mInstrumentation.runOnMainSync(() -> mSearchView.setQuery("Di", false));
        verify(mockQueryTextListener, times(1)).onQueryTextChange("Di");
    }

    public void testSuggestionSelection() {
        final SearchView.OnSuggestionListener mockSuggestionListener =
                spy(new MySuggestionListener());
        when(mockSuggestionListener.onSuggestionClick(anyInt())).thenCallRealMethod();

        final SearchView.OnQueryTextListener mockQueryTextListener =
                spy(new MyQueryTextListener());
        when(mockQueryTextListener.onQueryTextChange(anyString())).thenCallRealMethod();

        mInstrumentation.runOnMainSync(() -> {
                    mSearchView.setIconifiedByDefault(false);
                    mSearchView.setOnQueryTextListener(mockQueryTextListener);
                    mSearchView.setOnSuggestionListener(mockSuggestionListener);
                    mSearchView.requestFocus();
                });

        assertTrue(mSearchView.hasFocus());
        assertEquals(mSuggestionsAdapter, mSearchView.getSuggestionsAdapter());

        mInstrumentation.runOnMainSync(() -> mSearchView.setQuery("Di", false));
        mInstrumentation.waitForIdleSync();
        verify(mockQueryTextListener, times(1)).onQueryTextChange("Di");

        // Emulate click on the first suggestion - which should be Dido
        final int suggestionRowHeight = mActivity.getResources().getDimensionPixelSize(
                R.dimen.search_view_suggestion_row_height);
        ViewTestUtils.emulateTapOnScreen(mInstrumentation, mSearchView, mSearchView.getWidth() / 2,
                mSearchView.getHeight() + suggestionRowHeight / 2);

        // At this point we expect the click on the first suggestion to have activated a sequence
        // of events that ends up in our suggestion listener that sets the full suggestion text
        // as the current query. Some parts of this sequence of events are asynchronous, and those
        // are not "caught" by Instrumentation.waitForIdleSync - which is in general not a very
        // reliable way to wait for everything to be completed. As such, we are using our own
        // polling check mechanism to wait until the search view's query is the fully completed
        // suggestion for Dido. This check will time out and fail after a few seconds if anything
        // goes wrong during the processing of the emulated tap and the code never gets to our
        // suggestion listener
        PollingCheck.waitFor(() -> TextUtils.equals("Dido", mSearchView.getQuery()));

        // Just to be sure, verify that our spy suggestion listener was called
        verify(mockSuggestionListener, times(1)).onSuggestionClick(0);
        verifyNoMoreInteractions(mockSuggestionListener);
    }
}
