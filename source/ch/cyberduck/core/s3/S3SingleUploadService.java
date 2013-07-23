package ch.cyberduck.core.s3;

/*
 * Copyright (c) 2002-2013 David Kocher. All rights reserved.
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

import ch.cyberduck.core.DefaultIOExceptionMappingService;
import ch.cyberduck.core.MappingMimeTypeService;
import ch.cyberduck.core.Path;
import ch.cyberduck.core.PathContainerService;
import ch.cyberduck.core.Preferences;
import ch.cyberduck.core.exception.BackgroundException;
import ch.cyberduck.core.http.DelayedHttpEntityCallable;
import ch.cyberduck.core.http.ResponseOutputStream;
import ch.cyberduck.core.i18n.Locale;
import ch.cyberduck.core.io.BandwidthThrottle;
import ch.cyberduck.core.io.StreamListener;
import ch.cyberduck.core.transfer.TransferStatus;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.log4j.Logger;
import org.jets3t.service.ServiceException;
import org.jets3t.service.model.StorageObject;
import org.jets3t.service.utils.ServiceUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.Map;

/**
 * @version $Id$
 */
public class S3SingleUploadService {
    private static final Logger log = Logger.getLogger(S3SingleUploadService.class);

    private S3Session session;

    private PathContainerService containerService = new PathContainerService();

    public S3SingleUploadService(final S3Session session) {
        this.session = session;
    }

    public void upload(final Path file, final BandwidthThrottle throttle, final StreamListener listener,
                       final TransferStatus status) throws BackgroundException {
        InputStream in = null;
        ResponseOutputStream<StorageObject> out = null;
        MessageDigest digest = null;
        if(!Preferences.instance().getBoolean("s3.upload.metadata.md5")) {
            // Content-MD5 not set. Need to verify ourselves instad of S3
            try {
                digest = MessageDigest.getInstance("MD5");
            }
            catch(NoSuchAlgorithmException e) {
                log.error(e.getMessage());
            }
        }
        try {
            if(null == digest) {
                log.warn("MD5 calculation disabled");
                in = file.getLocal().getInputStream();
            }
            else {
                in = new DigestInputStream(file.getLocal().getInputStream(), digest);
            }
            final StorageObject object = this.createObjectDetails(file);
            out = this.write(file, object, status.getLength() - status.getCurrent(),
                    Collections.<String, String>emptyMap());
            try {
                session.upload(out, in, throttle, listener, status);
            }
            catch(IOException e) {
                throw new DefaultIOExceptionMappingService().map("Upload failed", e, file);
            }
        }
        catch(FileNotFoundException e) {
            throw new DefaultIOExceptionMappingService().map(e);
        }
        finally {
            IOUtils.closeQuietly(in);
            IOUtils.closeQuietly(out);
        }
        final StorageObject part = out.getResponse();
        if(null != digest) {
            session.message(MessageFormat.format(Locale.localizedString("Compute MD5 hash of {0}", "Status"), file.getName()));
            // Obtain locally-calculated MD5 hash.
            final String hexMD5 = ServiceUtils.toHex(digest.digest());
            try {
                session.getClient().verifyExpectedAndActualETagValues(hexMD5, part);
            }
            catch(ServiceException e) {
                throw new ServiceExceptionMappingService().map("Upload failed", e, file);
            }
        }
    }

    public ResponseOutputStream<StorageObject> write(final Path file,
                                                     final StorageObject part, final Long contentLength,
                                                     final Map<String, String> requestParams) throws BackgroundException {
        final DelayedHttpEntityCallable<StorageObject> command = new DelayedHttpEntityCallable<StorageObject>() {
            @Override
            public StorageObject call(final AbstractHttpEntity entity) throws BackgroundException {
                try {
                    session.getClient().putObjectWithRequestEntityImpl(
                            containerService.getContainer(file).getName(), part, entity, requestParams);
                }
                catch(ServiceException e) {
                    throw new ServiceExceptionMappingService().map("Upload failed", e, file);
                }
                return part;
            }

            @Override
            public long getContentLength() {
                return contentLength;
            }
        };
        return session.write(file, command);
    }

    protected StorageObject createObjectDetails(final Path file) throws BackgroundException {
        final StorageObject object = new StorageObject(containerService.getKey(file));
        final String type = new MappingMimeTypeService().getMime(file.getName());
        object.setContentType(type);
        if(Preferences.instance().getBoolean("s3.upload.metadata.md5")) {
            session.message(MessageFormat.format(
                    Locale.localizedString("Compute MD5 hash of {0}", "Status"), file.getName()));
            object.setMd5Hash(ServiceUtils.fromHex(file.getLocal().attributes().getChecksum()));
        }
        if(Preferences.instance().getBoolean("queue.upload.changePermissions")) {
            if(Preferences.instance().getProperty("s3.key.acl.default").equals("public-read")) {
                object.setAcl(session.getPublicCannedReadAcl());
            }
            else {
                // Owner gets FULL_CONTROL. No one else has access rights (default).
                object.setAcl(session.getPrivateCannedAcl());
            }
        }
        // Storage class
        if(StringUtils.isNotBlank(Preferences.instance().getProperty("s3.storage.class"))) {
            object.setStorageClass(Preferences.instance().getProperty("s3.storage.class"));
        }
        if(StringUtils.isNotBlank(Preferences.instance().getProperty("s3.encryption.algorithm"))) {
            object.setServerSideEncryptionAlgorithm(Preferences.instance().getProperty("s3.encryption.algorithm"));
        }
        // Default metadata for new files
        for(String m : Preferences.instance().getList("s3.metadata.default")) {
            if(StringUtils.isBlank(m)) {
                continue;
            }
            if(!m.contains("=")) {
                log.warn(String.format("Invalid header %s", m));
                continue;
            }
            int split = m.indexOf('=');
            String name = m.substring(0, split);
            if(StringUtils.isBlank(name)) {
                log.warn(String.format("Missing key in header %s", m));
                continue;
            }
            String value = m.substring(split + 1);
            if(StringUtils.isEmpty(value)) {
                log.warn(String.format("Missing value in header %s", m));
                continue;
            }
            object.addMetadata(name, value);
        }
        return object;
    }
}
