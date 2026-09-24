Asked: Write an EditProfile.java class for our Sprint 1 architecture that can edit both the student matching profile and the mentor profile depending on who is editing. Users should only be able to edit the extended attributes we own, not what lives in Shared Core, and edits should go through the PUT path instead of a new path.

Produced: A full EditProfile.java class with separate methods for mentors and students, type checks for every field, error handling for 400, 403 and 404, and our shared error format. It was around 200 lines, which was much longer than we needed.

Changed: We asked Claude to condense it down to just the class with one editProfile method under 30 lines. It uses the role from the token to choose mentor or student, rejects any field that is not one of our editable extended attributes, and saves the updated profile. We would need to move the field and rule checks into MentorProfile and StudentMatchProfile through an apply edits method.
