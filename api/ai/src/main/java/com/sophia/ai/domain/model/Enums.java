package com.sophia.ai.domain.model;

/** The small vocabularies the requests are written in. Kept together because each is only a few values. */
public final class Enums {

    private Enums() {
    }

    public enum StudyLevel {
        BEGINNER("a beginner who has never seen the subject: define every term and avoid jargon"),
        INTERMEDIATE("a student with the basics who needs the full picture and the connections between ideas"),
        ADVANCED("an advanced student: be precise and dense, assume the fundamentals, include subtleties and edge cases");

        private final String guidance;

        StudyLevel(String guidance) {
            this.guidance = guidance;
        }

        public String guidance() {
            return guidance;
        }
    }

    public enum Length {
        SHORT("about 400 words", 3000),
        MEDIUM("about 900 words", 5000),
        LONG("about 1800 words", 9000);

        private final String words;
        private final int maxTokens;

        Length(String words, int maxTokens) {
            this.words = words;
            this.maxTokens = maxTokens;
        }

        public String words() {
            return words;
        }

        public int maxTokens() {
            return maxTokens;
        }
    }

    /** Optional parts a generated note can carry. */
    public enum NotesInclude {
        KEY_TERMS("a glossary of the key terms"),
        EXAMPLES("worked examples"),
        SUMMARY("a short summary at the end"),
        QUESTIONS("5 practice questions at the end, without answers"),
        TABLES("a comparison table where several things are being contrasted");

        private final String instruction;

        NotesInclude(String instruction) {
            this.instruction = instruction;
        }

        public String instruction() {
            return instruction;
        }
    }

    public enum RewriteAction {
        SIMPLIFY(false, "Rewrite the text so a beginner can understand it: plain words, shorter sentences. Keep every important fact."),
        EXPAND(false, "Develop the text: add detail, context and a concrete example where they help. Keep the original ideas and order."),
        SHORTEN(false, "Condense the text to roughly half its length. Keep the key information and drop repetition and filler."),
        FORMAL(false, "Rewrite the text in a formal, academic tone without changing its meaning."),
        FRIENDLY(false, "Rewrite the text in a warm, conversational tone without changing its meaning."),
        FIX(false, "Correct spelling, grammar and punctuation. Change nothing else: keep the wording, tone and structure."),
        BULLETS(false, "Turn the text into a clear bulleted list, one idea per bullet, without losing information."),
        TRANSLATE(false, "Translate the text into the target language, keeping its formatting and tone."),
        ANALOGY(true, "Explain the idea in the passage with an everyday analogy, then say in one or two sentences how the analogy maps back to the real concept."),
        STEP_BY_STEP(true, "Explain the passage step by step as a numbered list, so each step builds on the previous one."),
        EXAMPLE(true, "Give two concrete, different examples that illustrate the idea in the passage, each in a sentence or two."),
        ELI10(true, "Explain the passage as if to a curious ten-year-old: simple words, a friendly tone, no jargon."),
        SUMMARIZE(true, "Summarize the passage in three to five bullets that someone could use to revise.");

        private final boolean addsText;
        private final String instruction;

        RewriteAction(boolean addsText, String instruction) {
            this.addsText = addsText;
            this.instruction = instruction;
        }

        /** True for the actions that produce new text to put next to the selection rather than replacing it. */
        public boolean addsText() {
            return addsText;
        }

        public String instruction() {
            return instruction;
        }
    }

    public enum DeckMode {
        /** A board of concept cards: one idea per card, in teaching order. */
        CONCEPTS,
        /** Question on the front, answer on the back. */
        FLASHCARDS
    }

    public enum MapKind {
        MINDMAP,
        CONCEPT_MAP
    }

    public enum Difficulty {
        EASY("recall and recognition of the main ideas"),
        MEDIUM("understanding: explaining, comparing, applying a rule to a simple case"),
        HARD("analysis: multi-step reasoning, edge cases, applying ideas to new situations"),
        MIXED("a spread: about 30% easy, 50% medium and 20% hard, ordered from easier to harder");

        private final String guidance;

        Difficulty(String guidance) {
            this.guidance = guidance;
        }

        public String guidance() {
            return guidance;
        }
    }

    public enum QuestionType {
        MULTIPLE_CHOICE,
        TRUE_FALSE,
        SHORT_ANSWER
    }

    /** Ready-made points of view for looking at a topic; a request may add its own as free text. */
    public enum Lens {
        HISTORICAL("historical: where the idea came from, how it developed, what it replaced"),
        SCIENTIFIC("scientific: the evidence, the method, what is established and what is still debated"),
        ECONOMIC("economic: costs, incentives, resources, who gains and who pays"),
        SOCIAL("social: effects on people, groups, culture and institutions"),
        ETHICAL("ethical: rights, duties, fairness, and the values in tension"),
        PRACTICAL("practical: how it is applied in real life, with examples and pitfalls"),
        CRITICAL("critical: weaknesses, assumptions, counter-arguments and limits"),
        EXPERT("expert: the fine points a specialist would raise"),
        BEGINNER("beginner: what a newcomer finds confusing and the simplest way in"),
        FUTURE("future: trends, open problems and where this may go");

        private final String description;

        Lens(String description) {
            this.description = description;
        }

        public String description() {
            return description;
        }
    }

    public enum DiagramKind {
        /** Steps with decisions and branches. */
        FLOWCHART,
        /** Events in chronological order. */
        TIMELINE,
        /** A process that loops back on itself. */
        CYCLE
    }

    public enum OcrMode {
        /** Everything that is written, as it is written. */
        TRANSCRIBE,
        /** Everything that is written, organised as clean notes (headings, lists, tables). */
        NOTES
    }

    public enum ChatMode {
        /** Answers questions from the chosen documents (or from general knowledge when none are chosen). */
        DOCUMENTS,
        /** A tutor that teaches by asking questions instead of giving answers. */
        SOCRATIC,
        /** A sparring partner that argues the other side of a motion. */
        DEBATE
    }

    /** The side the person takes in a debate; the assistant takes the other. */
    public enum Stance {
        PRO,
        CON
    }
}
