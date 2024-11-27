/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.media.router.cts;

import static android.media.cts.MediaRouterTestConstants.ROUTE_ID_REQUIRES_PERMISSIONS;
import static android.media.cts.MediaRouterTestConstants.ROUTE_NAME_REQUIRES_PERMISSIONS;

import android.media.MediaRoute2Info;

import com.android.media.flags.Flags;

import java.util.List;
import java.util.Set;


public class PermissionsRequiredRouteProvider extends BaseFakeRouteProviderService {

    public PermissionsRequiredRouteProvider() {
        super(createRoutes());
    }

    static List<MediaRoute2Info> createRoutes() {
        if (Flags.enableRouteVisibilityControlApi()) {
            return List.of(createPermissionsRequiredRoute(ROUTE_ID_REQUIRES_PERMISSIONS,
                    ROUTE_NAME_REQUIRES_PERMISSIONS,
                    Set.of("android.permission.POST_NOTIFICATIONS")));
        } else {
            return List.of();
        }
    }
}
