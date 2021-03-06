/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fxa_data.login;

import android.support.annotation.WorkerThread;
import org.mozilla.gecko.sync.ExtendedJSONObject;
import org.mozilla.gecko.sync.JSONRecordFetcher;
import org.mozilla.gecko.sync.delegates.JSONRecordFetchDelegate;
import org.mozilla.gecko.sync.net.AuthHeaderProvider;
import org.mozilla.gecko.sync.net.SyncStorageResponse;
import org.mozilla.gecko.tokenserver.TokenServerToken;
import org.mozilla.fxa_data.impl.FirefoxDataRequestUtils;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.util.Collection;

/** Static class that contains functions to access the collection info associated with a Firefox Account. */
class FirefoxSyncCollectionInfoAccessor {
    private FirefoxSyncCollectionInfoAccessor() {}

    interface CollectionInfoCallback {
        void onSuccess(Collection<String> existingCollectionNames);
        void onRequestFailure(Exception e);
        void onError(Exception e);
    }

    /**
     * Gets the names of collections that exist that are associated with the Firefox Account associated with the
     * given sync token.
     *
     * In theory, we can provide more result data (what is actually stored at those collections?) but I just don't need
     * that data right now.
     *
     * Both the request and callback occur on the calling thread (this is unintuitive: issue #3).
     */
    @WorkerThread // network request.
    static void getBlocking(final TokenServerToken token, final CollectionInfoCallback callback) {
        final String collectionInfoURI;
        try {
            collectionInfoURI = getCollectionInfoURI(token);
        } catch (final URISyntaxException e) {
            callback.onError(new Exception("Unable to get collection info URI", e));
            return;
        }

        final AuthHeaderProvider authHeaderProvider;
        try {
            authHeaderProvider = FirefoxDataRequestUtils.getAuthHeaderProvider(token);
        } catch (final URISyntaxException | UnsupportedEncodingException e) {
            callback.onError(new Exception("Unable to get collection info auth header provider."));
            return;
        }

        final JSONRecordFetcher fetcher = new JSONRecordFetcher(collectionInfoURI, authHeaderProvider);
        fetcher.fetch(new JSONRecordFetchDelegate() {
            @Override
            public void handleSuccess(final ExtendedJSONObject body) {
                // Consider caching result: issue #6.
                callback.onSuccess(body.keySet());
            }

            @Override
            public void handleFailure(final SyncStorageResponse response) {
                try {
                    callback.onRequestFailure(new Exception("Failed to retrieve collection info: " + response.getErrorMessage()));
                } catch (final IOException e) {
                    callback.onRequestFailure(new Exception("Failed to retrieve collection info or its error message"));
                }
            }

            @Override public void handleError(final Exception e) { callback.onError(e); }
        });
    }

    private static String getCollectionInfoURI(final TokenServerToken token) throws URISyntaxException {
        return FirefoxDataRequestUtils.getServerURI(token).toString() + "/info/collections";
    }
}
