# Original Software Publication (OSP) – SCP Checklist

This checklist summarizes all requirements for submitting a Software Track
paper (Original Software Publication) to *Science of Computer Programming*.

---

## 1. Basic Information
- [ ] Title of the software / framework
- [ ] Full author names (not anonymous)
- [ ] Correct affiliations
- [ ] Corresponding author indicated
- [ ] Contact emails
- [ ] ORCID 

---

## 2. Abstract
- [ ] Abstract included (mandatory)
- [ ] 100–150 words
- [ ] Focused on the software, not theory
- [ ] Describes:
  - problem context (RV + concurrency),
  - limitations of existing tools,
  - what the software does,
  - main guarantees,
  - implementation languages,
  - availability and reproducibility.

---

## 3. Keywords
- [ ] 4–6 keywords
- [ ] Relevant to runtime verification and the software
- [ ] Keywords do not count toward the page limit

---

## 4. Main Content (3–6 pages total)
(Includes abstract, text, figures, and diagrams)

- [ ] Introduction
- [ ] Contribution statement
- [ ] Software overview
- [ ] Architecture description
- [ ] Figures/diagrams explaining the system
- [ ] Reproducibility and usage (high-level)
- [ ] Availability section

❌ The following should NOT be in the paper:
- detailed examples,
- long command listings,
- extensive pseudocode,
- heavy theory or proofs.

---

## 5. Figures and Diagrams
- [ ] Included in the PDF
- [ ] Clear captions
- [ ] Help understand the software architecture
- [ ] Count toward the page limit

---

## 6. References
- [ ] Minimal and essential (≈ 5–10)
- [ ] Include RV 2024 paper associated with the software
- [ ] Do NOT count toward the page limit

---

## 7. Acknowledgments (recommended)
- [ ] Funding sources
- [ ] Research projects
- [ ] Do NOT count toward the page limit

---

## 8. CRediT Authorship Contribution Statement (recommended)
- [ ] Roles of each author clearly stated
- [ ] Standard Elsevier CRediT format
- [ ] Do NOT count toward the page limit

---

## 9. Declaration of Competing Interest (recommended)
- [ ] Statement included
- [ ] “No competing interests” if applicable
- [ ] Do NOT count toward the page limit

---

## 10. Software Metadata (MANDATORY)
Must be included as a table at the end of the paper.

- [ ] Software name
- [ ] Version
- [ ] Release date
- [ ] Authors
- [ ] Public repository URL
- [ ] DOI (e.g., Zenodo, if available)
- [ ] OSI-approved license (MIT, Apache-2.0, BSD, etc.)
- [ ] Programming languages
- [ ] Build system
- [ ] Supported operating systems
- [ ] Requirements
- [ ] Documentation files
- [ ] Support email

⚠️ Missing metadata leads to desk rejection.

---

## 11. Documentation and Examples (outside the paper)
- [ ] README.md
- [ ] User manual / Quick start
- [ ] Executable examples
- [ ] Reproducibility scripts

Examples must be in the documentation, not in the paper.

---

## 12. License
- [ ] OSI-approved license
- [ ] Declared in both repository and metadata

---

## Page Limit Reminder
- ✔ 3–6 pages INCLUDING abstract, text, and figures
- ❌ Excluding metadata, references, keywords, acknowledgments,
  CRediT, and conflict of interest sections

---

## Editorial Philosophy
The software is the primary contribution.
The paper serves as a concise description enabling understanding,
reuse, and reproducibility.