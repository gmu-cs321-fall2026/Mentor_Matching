Sprint 1 Retrospective
CS 321, Fall 2026 · Mason CareerLaunch · Subsystem 4: Mentor Matching
Subsystem scope: mentor and student matching profiles, and the mentor directory that later sprints match against.
Team members: Dillon Bajracharya, Jacob Livermon, Noah Carter, Rohan Veeranki, [FIFTH MEMBER'S NAME]

Sprint Goal: Met
Our goal was a walking skeleton of Subsystem 4: a mentor or a student can create, view and update their matching profile end to end through a containerized Java service behind the Shared Core. All three stories, namely, “create mentor profile”, “create student profile” and “edit Profile” work end to end, the docker image builds and runs.
What went well:
We committed the architecture doc and all four ADRs on September 22, before writing code, and those decisions help up: a single persistence boundary (ADR-001), shared Industry and GuidanceArea enums (ADR-002), and domain classes built around userId instead of inheriting Shared Core fields (ADR-003). Because the design settled the hard questions early, the layers fit together once we integrated. All five members contributed commits, and our AI prompt logs stayed current.
What went poorly:
EditProfile and matching class were written against MentorProfile that was still a stub, as a result, there were 11 compile errors, but we couldn’t catch those errors because we did not compile the whole project together early on, but later when we found those errors, we worked on them and fixed them. 
What we’re changing for Sprint 2:
We’re adding GitHub Actions workflow that compiles the project and runs docker build on every push, and main must be green before each Friday 4.30 pm sync. At planning, we'll agree on method signatures for shared classes before anyone builds on them, estimate and assign every story, and update Jira before each Friday sync.
Velocity:
Committed:13 points. Completed: 13 points. 5 points for creating Mentor Profile, 3 for creating student Profile and 5 for Edit Profile which is a total of 13 points.
