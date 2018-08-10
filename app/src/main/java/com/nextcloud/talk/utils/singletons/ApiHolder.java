/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2018 Mario Danic <mario@lovelyhq.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.utils.singletons;

import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.utils.database.user.UserUtils;

import java.util.HashMap;

import javax.inject.Inject;

import autodagger.AutoInjector;
import retrofit2.Retrofit;

@AutoInjector(NextcloudTalkApplication.class)
public class ApiHolder {
    private static final String TAG = "ApiHolder";

    private HashMap<Long, NcApi> ncApiHashMap;

    @Inject
    UserUtils userUtils;

    @Inject
    Retrofit retrofit;

    private static final ApiHolder holder = new ApiHolder();

    public static ApiHolder getInstance() {
        return holder;
    }

    public NcApi getNcApiInstanceForAccountId(long accountId, @Nullable String baseUrl) {
        NextcloudTalkApplication.getSharedApplication().getComponentApplication().inject(this);
        if (!ncApiHashMap.containsKey(accountId)) {
            UserEntity userAccount = userUtils.getUserWithId(accountId);
            if (userAccount == null || !TextUtils.isEmpty(baseUrl)) {
                retrofit = retrofit.newBuilder().baseUrl(baseUrl).build();
                return retrofit.create(NcApi.class);
            } else {
                retrofit = retrofit.newBuilder().baseUrl(userAccount.getBaseUrl()).build();
                ncApiHashMap.put(accountId, retrofit.create(NcApi.class));
            }
        }

        return ncApiHashMap.get(accountId);
    }
}