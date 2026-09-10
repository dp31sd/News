package dev.skidfuscator.backend.service;

import dev.skidfuscator.backend.session.ObfuscatorUserSession;
import dev.skidfuscator.protocol.SkidSessionData;

import java.io.IOException;
import java.util.zip.DataFormatException;

public interface ObfuscatorService {
    void create(final ObfuscatorUserSession userSession, final SkidSessionData session);

    void addFile(final ObfuscatorUserSession userSession, final byte[] data);

    void addMapping(final ObfuscatorUserSession userSession, final byte[] data);


    void execute(final ObfuscatorUserSession userSession) throws DataFormatException, IOException;
}
