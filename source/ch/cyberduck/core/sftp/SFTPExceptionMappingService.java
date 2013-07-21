package ch.cyberduck.core.sftp;

/*
 * Copyright (c) 2013 David Kocher. All rights reserved.
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
 * Bug fixes, suggestions and comments should be sent to:
 * dkocher@cyberduck.ch
 */

import ch.cyberduck.core.AbstractIOExceptionMappingService;
import ch.cyberduck.core.exception.BackgroundException;
import ch.cyberduck.core.exception.NotfoundException;
import ch.cyberduck.core.exception.QuotaException;

import java.io.IOException;
import java.net.SocketException;

import ch.ethz.ssh2.SFTPException;
import ch.ethz.ssh2.sftp.ErrorCodes;

/**
 * @version $Id$
 */
public class SFTPExceptionMappingService extends AbstractIOExceptionMappingService<IOException> {

    @Override
    public BackgroundException map(final IOException e) {
        final StringBuilder buffer = new StringBuilder();
        this.append(buffer, e.getMessage());
        if(e.getMessage().equals("Unexpected end of sftp stream.")) {
            return this.wrap(new SocketException(), buffer);
        }
        if(e instanceof SFTPException) {
            if(((SFTPException) e).getServerErrorCode() == ErrorCodes.SSH_FX_NO_SUCH_FILE) {
                return new NotfoundException(buffer.toString(), e);
            }
            if(((SFTPException) e).getServerErrorCode() == ErrorCodes.SSH_FX_NO_SUCH_PATH) {
                return new NotfoundException(buffer.toString(), e);
            }
            if(((SFTPException) e).getServerErrorCode() == ErrorCodes.SSH_FX_INVALID_HANDLE) {
                return new NotfoundException(buffer.toString(), e);
            }
            if(((SFTPException) e).getServerErrorCode() == ErrorCodes.SSH_FX_NOT_A_DIRECTORY) {
                return new NotfoundException(buffer.toString(), e);
            }
            if(((SFTPException) e).getServerErrorCode() == ErrorCodes.SSH_FX_QUOTA_EXCEEDED) {
                return new QuotaException(buffer.toString(), e);
            }
        }
        return this.wrap(e, buffer);
    }
}
