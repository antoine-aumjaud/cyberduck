package ch.cyberduck.core.onedrive;

/*
 * Copyright (c) 2002-2020 iterate GmbH. All rights reserved.
 * https://cyberduck.io/
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

import ch.cyberduck.core.AbstractPath;
import ch.cyberduck.core.Attributes;
import ch.cyberduck.core.DisabledListProgressListener;
import ch.cyberduck.core.Host;
import ch.cyberduck.core.ListService;
import ch.cyberduck.core.Path;
import ch.cyberduck.core.exception.BackgroundException;
import ch.cyberduck.core.features.AttributesFinder;
import ch.cyberduck.core.features.Home;
import ch.cyberduck.core.onedrive.features.sharepoint.SharepointSiteAttributesFinder;
import ch.cyberduck.core.ssl.X509KeyManager;
import ch.cyberduck.core.ssl.X509TrustManager;

import org.nuxeo.onedrive.client.types.Drive;
import org.nuxeo.onedrive.client.types.GroupItem;
import org.nuxeo.onedrive.client.types.Site;

import java.util.Deque;
import java.util.EnumSet;

import static ch.cyberduck.core.onedrive.SharepointListService.DRIVES_CONTAINER;
import static ch.cyberduck.core.onedrive.SharepointListService.SITES_CONTAINER;

public class SharepointSiteSession extends AbstractSharepointSession {
    public SharepointSiteSession(final Host host, final X509TrustManager trust, final X509KeyManager key) {
        super(host, trust, key);
    }

    @Override
    public boolean isSingleSite() {
        return true;
    }

    @Override
    public Site getSite(final Path file) throws BackgroundException {
        return Site.byId(getClient(), fileid.getFileId(file, new DisabledListProgressListener()));
    }

    @Override
    public GroupItem getGroup(final Path file) throws BackgroundException {
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T _getFeature(final Class<T> type) {
        if(type == ListService.class) {
            return (T) new SharepointSiteListService(this);
        }
        if(type == AttributesFinder.class) {
            return (T) new SharepointSiteAttributesFinder(this);
        }
        return super._getFeature(type);
    }

    @Override
    public ContainerItem getContainer(final Path file) {
        Deque<Path> pathDeque = decompose(file);

        Path lastContainer = null;
        Path lastCollection = null;
        boolean exit = false, nextExit = false;

        while(!exit && pathDeque.size() > 0) {
            final Path current = pathDeque.pop();
            exit = nextExit;

            switch(current.getName()) {
                case DRIVES_CONTAINER:
                    nextExit = true;
                case SITES_CONTAINER:
                    lastCollection = current;
                    break;

                default:
                    lastContainer = current;
            }
        }

        return new ContainerItem(lastContainer, lastCollection, exit);
    }
}
