import java.util.ArrayList;
import java.util.List;

/**
 * A mentor in the system: has areas of expertise and a cap on
 * how many students they can take on at once.
 */
public class MentorProfile extends ProfileData {
    private List<String> expertiseAreas;
    private int yearsOfExperience;
    private int maxStudents;
    private int currentStudentCount;

    public MentorProfile(String name, String email, String bio,
                          int yearsOfExperience, int maxStudents) {
        super(name, email, bio);
        this.expertiseAreas = new ArrayList<>();
        this.yearsOfExperience = yearsOfExperience;
        this.maxStudents = maxStudents;
        this.currentStudentCount = 0;
    }

    public void addExpertise(String area) {
        expertiseAreas.add(area);
    }

    public List<String> getExpertiseAreas() {
        return expertiseAreas;
    }

    public int getYearsOfExperience() {
        return yearsOfExperience;
    }

    public int getMaxStudents() {
        return maxStudents;
    }

    public int getCurrentStudentCount() {
        return currentStudentCount;
    }

    public boolean hasCapacity() {
        return currentStudentCount < maxStudents;
    }

    /** Call this once a student is matched to this mentor. */
    public void assignStudent() {
        if (hasCapacity()) {
            currentStudentCount++;
        }
    }
}
