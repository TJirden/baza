package cringe.baza.analysis;

import cringe.baza.domain.CensorshipResult;

@FunctionalInterface
public interface MemeCensorshipService {
    CensorshipResult checkCensorship(String fileId);
}
