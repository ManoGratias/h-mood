package com.hmood.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.CookieManager;
import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Cookies du navigateur intégré (lecteur officiel Deezer) conservés entre deux lancements,
 * pour que l'utilisateur reste connecté à son compte. Fichier local uniquement.
 */
public final class PersistentCookieStore implements CookieStore {

    private record Saved(String uri, String name, String value, String domain, String path, long maxAge,
                         boolean secure, boolean httpOnly) {
    }

    private final CookieStore delegate = new CookieManager().getCookieStore();
    private final Path file;
    private final ObjectMapper json = new ObjectMapper();

    public PersistentCookieStore(Path file) {
        this.file = file;
        load();
    }

    private void load() {
        try {
            if (!Files.isRegularFile(file)) return;
            List<Saved> saved = json.readValue(file.toFile(), new TypeReference<>() { });
            for (Saved s : saved) {
                HttpCookie c = new HttpCookie(s.name(), s.value());
                c.setDomain(s.domain());
                c.setPath(s.path());
                c.setMaxAge(s.maxAge());
                c.setSecure(s.secure());
                c.setHttpOnly(s.httpOnly());
                c.setVersion(0);
                delegate.add(s.uri() == null ? null : URI.create(s.uri()), c);
            }
        } catch (Exception e) {
            // fichier illisible : on repart sans cookies (l'utilisateur se reconnectera)
        }
    }

    public synchronized void save() {
        try {
            List<Saved> out = new ArrayList<>();
            for (URI uri : delegate.getURIs()) {
                for (HttpCookie c : delegate.get(uri)) {
                    if (c.hasExpired()) continue;
                    out.add(new Saved(uri.toString(), c.getName(), c.getValue(), c.getDomain(), c.getPath(),
                            c.getMaxAge(), c.getSecure(), c.isHttpOnly()));
                }
            }
            Files.createDirectories(file.getParent());
            json.writeValue(file.toFile(), out);
        } catch (Exception e) {
            // échec d'enregistrement sans conséquence
        }
    }

    public synchronized void clearAll() {
        delegate.removeAll();
        save();
    }

    @Override public void add(URI uri, HttpCookie cookie) { delegate.add(uri, cookie); }
    @Override public List<HttpCookie> get(URI uri) { return delegate.get(uri); }
    @Override public List<HttpCookie> getCookies() { return delegate.getCookies(); }
    @Override public List<URI> getURIs() { return delegate.getURIs(); }
    @Override public boolean remove(URI uri, HttpCookie cookie) { return delegate.remove(uri, cookie); }
    @Override public boolean removeAll() { return delegate.removeAll(); }
}
