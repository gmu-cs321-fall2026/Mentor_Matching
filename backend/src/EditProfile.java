import java.util.*;

public class EditProfile {

    public Object editProfile(ProfileData data, UUID userId, String role, Map<String, Object> body) {
        boolean isMentor = "Mentor".equalsIgnoreCase(role);
        Set<String> editable = isMentor
                ? Set.of("guidanceAreas", "technicalDomains", "maxMentees", "acceptingMentees")
                : Set.of("guidanceWanted", "targetCompanies", "preferSameProgram");

        for (String field : body.keySet())
            if (!editable.contains(field))
                throw new IllegalArgumentException(field + " cannot be edited");

        if (isMentor) {
            MentorProfile profile = data.findMentor(userId);
            profile.applyEdits(body);
            return data.updateMentor(profile);
        }
        StudentMatchProfile profile = data.findStudent(userId);
        profile.applyEdits(body);
        return data.updateStudent(profile);
    }
}
