package ch.cyberduck.core.azure;

/*
 * Copyright (c) 2002-2014 David Kocher. All rights reserved.
 * http://cyberduck.ch/
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * Bug fixes, suggestions and comments should be sent to feedback@cyberduck.ch
 */

import ch.cyberduck.core.AbstractProtocol;
import ch.cyberduck.core.Host;
import ch.cyberduck.core.Scheme;
import ch.cyberduck.core.Session;

/**
 * @version $Id:$
 */
public class AzureProtocol extends AbstractProtocol {

    @Override
    public Session createSession(Host host) {
        return new AzureSession(host);
    }

    /**
     * URL format: Blobs are addressable using the following URL format:
     * http://<storage account>.blob.core.windows.net/<container>/<blob>
     */
    @Override
    public String getDefaultHostname() {
        return "blob.core.windows.net";
    }

    @Override
    public String getUsernamePlaceholder() {
        return "Storage Account Name";
    }

    @Override
    public String getPasswordPlaceholder() {
        return "Primary Access Key";
    }

    @Override
    public boolean isAnonymousConfigurable() {
        return false;
    }

    @Override
    public boolean isHostnameConfigurable() {
        return true;
    }

    @Override
    public String getIdentifier() {
        return "azure";
    }

    @Override
    public String getDescription() {
        return "Windows Azure Blob Storage ";
    }

    @Override
    public Scheme getScheme() {
        return Scheme.https;
    }
}
