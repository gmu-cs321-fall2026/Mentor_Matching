import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Sprint 1 {@link ProfileData}: our attributes in memory, Shared Core's
 * attributes read through {@link SharedCoreClient} on every view.
 *
 * <p>ADR-001 chose this so we could demo and test without waiting for the
 * attribute split or a database. Two consequences the team should keep in mind:
 * profiles reset when the container restarts, and every profile read costs one
 * call to Shared Core.
 *
 * <p>{@code DB_URL} is read and reported at startup so the Week 13 compose
 * environment can already wire it. Moving to PostgreSQL means a second
 * implementation of the interface, not a change to this one.
 */
final class InMemoryProfileData implements ProfileData {

    private final SharedCoreClient sharedCore;
    private final Map<UUID, MentorProfile> mentors = new ConcurrentHashMap<>();
    private final Map<UUID, StudentMatchProfile> students = new ConcurrentHashMap<>();

    InMemoryProfileData(SharedCoreClient sharedCore) {
        this.sharedCore = sharedCore;
    }

    @Override
    public Map<String, Object> saveMentor(MentorProfile profile) {
        require(profile.validate());
        if (mentors.putIfAbsent(profile.getUserId(), profile) != null) {
            throw ApiException.conflict("this mentor already has a profile; use PUT to change it");
        }
        pushSharedCoreAttributes(profile.getUserId());
        return mentorView(profile);
    }

    @Override
    public MentorProfile findMentor(UUID userId) {
        MentorProfile profile = mentors.get(userId);
        if (profile == null) {
            throw ApiException.notFound("no mentor profile for " + userId);
        }
        return profile;
    }

    @Override
    public Map<String, Object> updateMentor(MentorProfile profile) {
        require(profile.validate());
        mentors.put(profile.getUserId(), profile);
        pushSharedCoreAttributes(profile.getUserId());
        return mentorView(profile);
    }

    @Override
    public Map<String, Object> saveStudent(StudentMatchProfile profile) {
        require(profile.validate());
        if (students.putIfAbsent(profile.getUserId(), profile) != null) {
            throw ApiException.conflict("this student already has a profile; use PUT to change it");
        }
        pushSharedCoreAttributes(profile.getUserId());
        return studentView(profile);
    }

    @Override
    public StudentMatchProfile findStudent(UUID userId) {
        StudentMatchProfile profile = students.get(userId);
        if (profile == null) {
            throw ApiException.notFound("no student matching profile for " + userId);
        }
        return profile;
    }

    @Override
    public Map<String, Object> updateStudent(StudentMatchProfile profile) {
        require(profile.validate());
        students.put(profile.getUserId(), profile);
        pushSharedCoreAttributes(profile.getUserId());
        return studentView(profile);
    }

    @Override
    public Map<String, Object> listMentors(Industry industry, GuidanceArea guidance,
            boolean withCapacityOnly, int page, int size) {
        List<MentorProfile> matches = new ArrayList<>();
        for (MentorProfile profile : mentors.values()) {
            if (industry != null && profile.getIndustry() != industry) {
                continue;
            }
            if (guidance != null && !profile.getGuidanceAreas().contains(guidance)) {
                continue;
            }
            if (withCapacityOnly && !profile.hasCapacity()) {
                continue;
            }
            matches.add(profile);
        }
        // Stable order, so paging never repeats or skips a mentor between calls.
        matches.sort(Comparator.comparing(profile -> profile.getUserId().toString()));

        int total = matches.size();
        int totalPages = total == 0 ? 0 : (total + size - 1) / size;
        int from = Math.min((page - 1) * size, total);
        int to = Math.min(from + size, total);

        List<Map<String, Object>> items = new ArrayList<>();
        for (MentorProfile profile : matches.subList(from, to)) {
            items.add(mentorView(profile));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", items);
        body.put("page", page);
        body.put("size", size);
        body.put("total", total);
        body.put("totalPages", totalPages);
        return body;
    }

    @Override
    public Map<String, Object> mentorView(MentorProfile profile) {
        Map<String, Object> view = baseView(profile.getUserId());
        view.put("industry", profile.getIndustry());
        view.put("guidanceAreas", new ArrayList<>(profile.getGuidanceAreas()));
        view.put("technicalDomains", profile.getTechnicalDomains());
        view.put("hoursPerMonth", profile.getHoursPerMonth());
        view.put("contactMethod", profile.getContactMethod());
        view.put("maxMentees", profile.getMaxMentees());
        view.put("activeMentees", profile.getActiveMentees());
        view.put("acceptingMentees", profile.isAcceptingMentees());
        view.put("hasCapacity", profile.hasCapacity());
        return view;
    }

    @Override
    public Map<String, Object> studentView(StudentMatchProfile profile) {
        Map<String, Object> view = baseView(profile.getUserId());
        view.put("targetIndustries", new ArrayList<>(profile.getTargetIndustries()));
        view.put("targetRoles", profile.getTargetRoles());
        view.put("guidanceWanted", new ArrayList<>(profile.getGuidanceWanted()));
        view.put("targetCompanies", profile.getTargetCompanies());
        view.put("preferSameProgram", profile.isPreferSameProgram());
        return view;
    }

    /**
     * userId plus whatever Shared Core knows about the person.
     *
     * <p>Shared Core's own {@code id}/{@code userId} keys are dropped so the
     * merged object carries exactly one identifier.
     */
    private Map<String, Object> baseView(UUID userId) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("userId", userId.toString());
        Map<String, Object> user = sharedCore.getUser(userId);
        user.remove("id");
        user.remove("userId");
        view.putAll(user);
        return view;
    }

    /**
     * The {@code PUT /users/{id}} step of the architecture's sequence diagram.
     *
     * <p>Sends nothing in Sprint 1: every attribute our classes hold is either
     * ours to keep or still marked TBD in the data model, and writing a TBD
     * attribute into Shared Core would prejudge that decision. The seam exists
     * so settling the split is a change here and nowhere else.
     */
    private void pushSharedCoreAttributes(UUID userId) {
        Map<String, Object> owned = new LinkedHashMap<>();
        // Once the attribute split is agreed, put Shared Core-owned attributes
        // in this map. See the open questions in /docs/architecture.md.
        sharedCore.updateUser(userId, owned);
    }

    private static void require(Map<String, String> fieldErrors) {
        if (!fieldErrors.isEmpty()) {
            throw ApiException.fields(fieldErrors);
        }
    }
}
