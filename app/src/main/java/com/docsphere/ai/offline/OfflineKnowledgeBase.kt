package com.docsphere.ai.offline

import java.util.Locale
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Large-Scale On-Device Knowledge Base and Conversational Engine for DOCSPHERE.
 * Provides over 35+ encyclopedic domains completely offline:
 * - Document & File Standards (PDF, Word, Excel, PowerPoint, Text, Markdown, Code, Compression)
 * - Document Security, Cryptography & Encryption (AES-256, Digital Signatures, Hashes)
 * - Business, Accounting & Financial Instruments (Invoices, Receipts, Balance Sheets, Taxes)
 * - Legal Agreements & Corporate Contracts (NDAs, SLAs, MOUs, Employment)
 * - Academic & Engineering Project Standards (CIA Reviews, Abstracts, IEEE, System Architecture)
 * - Computer Science, Data Structures & Algorithms
 * - Artificial Intelligence, LLMs, Neural Networks & On-Device RAG
 * - Cyber Security & Data Privacy
 * - Career Guides & ATS Resume Construction
 * - Mathematics, Unit Conversions & Productivity Frameworks
 */
@Singleton
class OfflineKnowledgeBase @Inject constructor() {

    data class KnowledgeEntry(
        val title: String,
        val tags: List<String>,
        val content: String
    )

    // ── Extensive Offline Knowledge Dataset ──────────────────────────────────
    private val knowledgeDataset = listOf(
        // ── 1. Document & File Formats ───────────────────────────────────────
        KnowledgeEntry(
            title = "PORTABLE DOCUMENT FORMAT (PDF)",
            tags = listOf("pdf", "adobe", "portable document", "read pdf", "view pdf", "pdf standard"),
            content = """
                📄 **PDF (Portable Document Format):**
                
                • **Definition**: An ISO 32000 standard originally developed by Adobe in 1992 to present documents independently of software, hardware, or operating systems.
                • **Key Advantages**:
                  - Preserves fonts, images, vector graphics, and exact millimeter layouts.
                  - Supports encryption (AES-256), permission locks, and PKI digital signatures.
                  - Universally viewable on mobile, desktop, and web browsers.
                • **Common Subsets**:
                  - **PDF/A**: Standardized for long-term digital preservation and archival.
                  - **PDF/X**: Optimized for professional graphics exchange and pre-press printing.
                • **Best Used For**: Contracts, official certificates, invoices, resumes, and published papers.
            """.trimIndent()
        ),
        KnowledgeEntry(
            title = "MICROSOFT WORD (DOCX / DOC)",
            tags = listOf("docx", "doc", "word", "microsoft word", "word document", "office document"),
            content = """
                📝 **DOCX (Microsoft Word Document):**
                
                • **Definition**: An XML-based open document standard (Office Open XML / ISO/IEC 29500) introduced in Microsoft Word 2007.
                • **Architecture**: A DOCX file is essentially a ZIP archive containing `word/document.xml`, formatting styles, and embedded media.
                • **Differences vs PDF**:
                  - Designed for collaborative real-time authoring, editing, track-changes, and dynamic styling.
                  - Visual rendering may shift slightly across different office viewers and missing fonts.
                • **Tip in DOCSPHERE**: Always export finalized DOCX documents to PDF before distribution to freeze exact layout and prevent unauthorized editing.
            """.trimIndent()
        ),
        KnowledgeEntry(
            title = "SPREADSHEETS & TABULAR DATA (XLSX / CSV)",
            tags = listOf("xlsx", "xls", "csv", "excel", "spreadsheet", "sheets", "workbook"),
            content = """
                📊 **Spreadsheets (XLSX & CSV):**
                
                • **XLSX (Microsoft Excel)**: A multi-sheet workbook package storing complex calculations, cell formatting, data validations, pivot tables, and charts.
                • **CSV (Comma-Separated Values)**: Plain-text tabular data where records are delimited by newlines and fields by commas.
                • **Best Practices**:
                  - **Use CSV** for raw data pipelines, database dumps, and ML model training datasets.
                  - **Use XLSX** when business logic requires formulas (e.g. `VLOOKUP`, `XLOOKUP`, `INDEX/MATCH`, `SUMIFS`), colored reports, and multiple tabs.
            """.trimIndent()
        ),
        KnowledgeEntry(
            title = "PRESENTATIONS (PPTX / PPT / SLIDES)",
            tags = listOf("pptx", "ppt", "powerpoint", "slides", "presentation", "deck"),
            content = """
                📽️ **PowerPoint & Presentation Decks (PPTX):**
                
                • **Definition**: An Open XML format packaging slide layouts, embedded vector diagrams, typography, notes, and animations.
                • **Presentation Best Practices (10/20/30 Rule)**:
                  - Limit slides to key bullet points; avoid paragraphs of dense text.
                  - High contrast: light text on dark backgrounds or vice-versa.
                  - Support each claim with a diagram, chart, or architecture flow.
            """.trimIndent()
        ),
        KnowledgeEntry(
            title = "TEXT & MARKDOWN (TXT / MD)",
            tags = listOf("txt", "text", "md", "markdown", "plain text", "readme"),
            content = """
                📑 **Plain Text (TXT) & Markdown (MD):**
                
                • **Plain Text (.txt)**: Zero formatting, maximum longevity. Readable by any device created since 1970.
                • **Markdown (.md)**: Lightweight markup language created by John Gruber in 2004 that converts readable text into structured HTML:
                  - `# Heading 1`, `## Heading 2`, `### Heading 3`
                  - `**Bold text**`, `*Italics*`, `` `inline code` ``
                  - Lists: `- bullet` or `1. numbered`
                • **Why It Matters**: Markdown is the lingua franca of documentation, GitHub READMEs, and AI chat interfaces!
            """.trimIndent()
        ),
        KnowledgeEntry(
            title = "FILE ARCHIVES & COMPRESSION (ZIP / RAR / 7Z)",
            tags = listOf("zip", "rar", "7z", "compression", "archive", "compress", "tar", "gz"),
            content = """
                🗜️ **File Compression & Archiving:**
                
                • **Lossless Compression**: Mathematically reduces file redundancy (e.g. DEFLATE, LZMA) without losing a single bit of information.
                • **Key Formats**:
                  - **ZIP**: Universally supported natively on Windows, macOS, Android, and Linux.
                  - **7z**: Delivers significantly higher compression ratios using the LZMA/LZMA2 algorithms.
                  - **TAR.GZ**: The standard packaging format in Linux servers and software distributions.
                • **Tip**: Compress collections of related files before emailing or cloud backup to preserve folder structure and save bandwidth.
            """.trimIndent()
        ),

        // ── 2. Security, Cryptography & Encryption ───────────────────────────
        KnowledgeEntry(
            title = "DOCUMENT ENCRYPTION & DATA SECURITY (AES-256)",
            tags = listOf("encryption", "aes", "aes-256", "security", "protect", "password", "crypto"),
            content = """
                🔒 **Document Encryption & Security (AES-256):**
                
                • **AES-256 (Advanced Encryption Standard)**: A symmetric-key block cipher adopted by governments and financial institutions worldwide.
                • **Security Strength**: Uses a 256-bit key length. Brute-forcing an AES-256 key would take billions of years using modern supercomputers.
                • **At-Rest vs In-Transit**:
                  - **At-Rest**: Encrypting files saved on disk (e.g. Android file-based encryption).
                  - **In-Transit**: Protecting files traveling across networks using TLS 1.3.
                • **DOCSPHERE Protection**: On-device files are isolated within Android's private app sandbox.
            """.trimIndent()
        ),
        KnowledgeEntry(
            title = "DIGITAL SIGNATURES & HASH FUNCTIONS (SHA-256 / PKI)",
            tags = listOf("digital signature", "hash", "sha-256", "pki", "checksum", "tamper", "certificate"),
            content = """
                ✍️ **Digital Signatures & Cryptographic Hashes:**
                
                • **Cryptographic Hash (SHA-256)**: Produces a unique 64-character hexadecimal fingerprint of any file. If a single comma in a 500-page document changes, the hash changes completely (avalanche effect).
                • **Digital Signature**: Uses Public Key Cryptography (Asymmetric RSA / ECDSA):
                  1. The signer creates a hash of the document.
                  2. The hash is encrypted with the signer's Private Key.
                  3. Anyone with the Public Key can verify that the document is authentic and has not been altered.
                • **Legal Validity**: In most jurisdictions (e.g. US ESIGN Act, EU eIDAS, India IT Act), verified digital signatures carry the full weight of handwritten signatures.
            """.trimIndent()
        ),
        KnowledgeEntry(
            title = "OPTICAL CHARACTER RECOGNITION (OCR)",
            tags = listOf("ocr", "scan", "text recognition", "extract text", "image to text", "scanned document"),
            content = """
                🔍 **Optical Character Recognition (OCR):**
                
                • **How It Works**: OCR analyzes pixel matrices, identifies stroke patterns, contours, and character features, and converts image pixels into machine-readable digital text.
                • **Best Scanning Practices**:
                  - Maintain 300 DPI resolution for document scans.
                  - Ensure high contrast between text and background with uniform lighting.
                  - Straighten skewed images before OCR for optimal character recognition.
                • **DOCSPHERE Implementation**: Uses on-device Google ML Kit neural text recognition, processing receipts and scans 100% locally with zero cloud leakage.
            """.trimIndent()
        ),

        // ── 3. Business, Finance & Accounting ────────────────────────────────
        KnowledgeEntry(
            title = "INVOICES VS RECEIPTS",
            tags = listOf("invoice", "receipt", "bill", "payment", "finance", "accounting", "billing"),
            content = """
                💰 **Invoices vs Receipts:**
                
                • **Invoice**: A formal billing request sent by a seller to a buyer *before* payment is made.
                  - Contains: Invoice number, issue date, payment due date (e.g. Net 30), itemized description of goods/services, unit prices, tax (GST/VAT), and payment instructions.
                • **Receipt**: A proof of transaction issued *after* payment has been completed.
                  - Contains: Receipt number, amount paid, payment method (Credit Card, UPI, Bank Transfer), date received, and remaining balance (usually zero).
                • **Accounting Tip**: Invoices record Accounts Receivable / Payable; receipts prove cash disbursements for tax audits.
            """.trimIndent()
        ),
        KnowledgeEntry(
            title = "FINANCIAL STATEMENTS (BALANCE SHEET / P&L / CASH FLOW)",
            tags = listOf("balance sheet", "p&l", "profit and loss", "cash flow", "financial statement", "income statement"),
            content = """
                📈 **The Three Core Financial Statements:**
                
                1. **Balance Sheet**: A snapshot of financial health at a specific date:
                   - `Assets = Liabilities + Shareholders' Equity`
                   - Lists cash, accounts receivable, inventory vs debts and payables.
                2. **Income Statement (Profit & Loss / P&L)**: Shows performance over a period:
                   - `Revenue - Cost of Goods Sold (COGS) = Gross Profit`
                   - `Gross Profit - Operating Expenses = Net Income`
                3. **Cash Flow Statement**: Tracks actual cash entering and leaving the business:
                   - Operating, Investing, and Financing activities.
            """.trimIndent()
        ),
        KnowledgeEntry(
            title = "TAX DOCUMENTS & FILING (GST / VAT / W-2 / 1099)",
            tags = listOf("tax", "gst", "vat", "w2", "1099", "income tax", "deductions", "pan"),
            content = """
                🏛️ **Tax Terminology & Document Overview:**
                
                • **GST / VAT**: Goods and Services Tax / Value Added Tax applied at each stage of the supply chain. Businesses claim Input Tax Credit (ITC) on taxes paid on purchases.
                • **W-2 (US)**: Wage and Tax Statement reporting annual wages and taxes withheld for employees.
                • **1099 (US)**: Tax form reporting non-employee earnings for freelancers and independent contractors.
                • **PAN / TIN**: Permanent Account Number / Taxpayer Identification Number used to identify taxpayers and track revenue transactions.
            """.trimIndent()
        ),

        // ── 4. Legal Contracts & Agreements ──────────────────────────────────
        KnowledgeEntry(
            title = "NON-DISCLOSURE AGREEMENTS (NDA)",
            tags = listOf("nda", "non disclosure", "agreement", "contract", "confidentiality", "proprietary"),
            content = """
                📜 **Non-Disclosure Agreements (NDA):**
                
                • **Purpose**: A legally binding contract establishing confidential relationships where parties agree not to disclose specified proprietary information.
                • **Key Types**:
                  - **Unilateral**: One party discloses confidential info to another (e.g. startup pitching an investor).
                  - **Mutual / Bilateral**: Both parties share sensitive data (e.g. joint venture, merger, or vendor evaluation).
                • **Crucial Clauses**:
                  - Definition of Confidential Information.
                  - Standard Exclusions (publicly available information, independently developed data).
                  - Term Duration (typically 1 to 3 years) and Governing Law jurisdiction.
            """.trimIndent()
        ),
        KnowledgeEntry(
            title = "SERVICE LEVEL AGREEMENTS (SLA)",
            tags = listOf("sla", "service level agreement", "uptime", "contract", "service", "downtime"),
            content = """
                ⏱️ **Service Level Agreements (SLA):**
                
                • **Definition**: A commitment between a service provider and a client documenting expected quality, availability, and responsibilities.
                • **Key Metrics**:
                  - **Uptime Percentage**: Often specified as "Nines" (e.g. 99.9% uptime permits max 8.76 hours downtime per year; 99.99% allows ~52 minutes).
                  - **Mean Time to Resolution (MTTR)**: Expected recovery speed after an incident.
                  - **Service Credits / Penalties**: Financial remedies owed to the customer if SLA thresholds are breached.
            """.trimIndent()
        ),
        KnowledgeEntry(
            title = "MEMORANDUM OF UNDERSTANDING (MOU)",
            tags = listOf("mou", "memorandum", "letter of intent", "loi", "partnership"),
            content = """
                🤝 **Memorandum of Understanding (MOU):**
                
                • **Definition**: A formal document describing a mutual agreement between parties expressing a convergence of will and indicating an intended common line of action.
                • **Legal Status**: Typically softer than a full contract and often non-binding (unless specific language stipulates binding obligations).
                • **Common Use**: Establishing academic partnerships, initial joint ventures, government collaborations, or pre-contractual negotiations.
            """.trimIndent()
        ),

        // ── 5. Career & Professional Writing ─────────────────────────────────
        KnowledgeEntry(
            title = "BUILDING AN ATS-FRIENDLY RESUME / CV",
            tags = listOf("resume", "cv", "curriculum vitae", "ats", "job application", "career", "interview", "hiring"),
            content = """
                💼 **Guide: Building an ATS-Friendly Resume:**
                
                1. **ATS Optimization (Applicant Tracking System)**:
                   - Use standard clean single-column or clear dual-column formatting without complex graphics, charts, or text boxes that confuse parsers.
                   - Save as **PDF** to ensure layout stability across screen sizes.
                2. **Structure**:
                   - **Header**: Full Name, Phone, Professional Email, LinkedIn, Portfolio.
                   - **Summary**: 3 concise sentences highlighting years of experience, core technical stack, and your biggest career achievement.
                   - **Experience**: Reverse chronological order. Start bullet points with strong action verbs (*Engineered, Architected, Accelerated, Reduced*).
                   - **Quantified Impact**: Follow the Google XYZ formula: *"Accomplished [X], as measured by [Y], by doing [Z]"* (e.g. *"Decreased API latency by 45% by implementing Redis caching"*).
                   - **Skills**: Categorized into Languages, Frameworks, Tools, and Methodologies.
                   - **Education & Certifications**: Degree, institution, graduation year, honors.
            """.trimIndent()
        ),
        KnowledgeEntry(
            title = "PROFESSIONAL EMAIL ETIQUETTE & COVER LETTERS",
            tags = listOf("cover letter", "email", "etiquette", "formal email", "application letter", "communication"),
            content = """
                ✉️ **Professional Email & Cover Letter Blueprint:**
                
                • **Subject Line**: Clear, concise, and searchable (e.g. `Application: Full Stack Engineer – Alex Taylor`).
                • **Greeting**: Formal (`Dear Ms. Smith,` or `Dear Hiring Team,`). Avoid `Hey` or `To whom it may concern`.
                • **Body Paragraphs**:
                  1. Opening: State purpose of communication and enthusiasm.
                  2. Value Proposition: Cite 1–2 relevant project achievements directly applicable to their challenges.
                  3. Call to Action: Propose a short call or interview.
                • **Sign-off**: `Sincerely,` or `Best regards,` followed by your name and phone number.
            """.trimIndent()
        ),

        // ── 6. Academic & Engineering Projects ───────────────────────────────
        KnowledgeEntry(
            title = "ENGINEERING PROJECT EVALUATION (CIA & REVIEWS)",
            tags = listOf("cia", "project", "evaluation", "engineering", "krct", "anna university", "review", "viva"),
            content = """
                🎓 **Academic Engineering Project Lifecycle (CIA Reviews):**
                
                • **Phase 1 (CIA-1)**:
                  - **Problem Definition**: Identification of the real-world engineering challenge, baseline limitations, and motivation.
                  - **Objectives & Scope**: Clear measurable targets (e.g. *"Develop on-device edge AI search with < 200ms latency"*).
                  - **Literature Survey**: Review of recent IEEE papers, existing products, and research gap identification.
                • **Phase 2 (CIA-2)**:
                  - **System Architecture**: High-level block diagrams, data flow diagrams (DFD), sequence diagrams, and technology stack selection.
                  - **Module Implementation Progress**: Core algorithm development, database schema, and test setup.
                  - **Experimental Validation**: Preliminary metrics, benchmark comparisons, and GUI demonstrations.
                • **Phase 3 (Final Viva)**:
                  - Complete functional prototype, test cases, user studies, conclusion, and future enhancements.
            """.trimIndent()
        ),
        KnowledgeEntry(
            title = "HOW TO WRITE A RESEARCH ABSTRACT",
            tags = listOf("abstract", "research paper", "ieee", "paper", "journal", "publication"),
            content = """
                📝 **The 4-Sentence Research Abstract Formula:**
                
                1. **Background & Problem**: State the overarching context and the critical gap in existing solutions (1–2 sentences).
                2. **Proposed Solution**: Introduce your system/algorithm/methodology clearly naming its core innovation (1 sentence).
                3. **Methodology & Architecture**: Summarize how the solution was implemented and tested (1–2 sentences).
                4. **Results & Impact**: State the quantitative outcome achieved (e.g. *"Experimental results demonstrate a 94.2% accuracy improvement with 3.8x faster inference"*).
                
                💡 *Tip: Keep abstracts under 250 words and avoid abbreviations or citations inside the abstract!*
            """.trimIndent()
        ),
        KnowledgeEntry(
            title = "IEEE FORMATTING & PLAGIARISM GUIDELINES",
            tags = listOf("ieee", "plagiarism", "citation", "apa", "mla", "turnitin", "urkund", "references"),
            content = """
                📚 **IEEE Formatting & Plagiarism Prevention:**
                
                • **IEEE Style**: Two-column layout, 10pt Times New Roman, numbered citations enclosed in square brackets (e.g. `[1]`, `[2]-[4]`).
                • **Plagiarism Standards**:
                  - Keep overall similarity index below 15% (and < 1% from any single source).
                  - Always cite original authors when paraphrasing concepts or adopting algorithms.
                  - Enclose verbatim phrases in quotation marks with explicit page citations.
            """.trimIndent()
        ),

        // ── 7. Computer Science & Software Engineering ───────────────────────
        KnowledgeEntry(
            title = "DATA STRUCTURES ESSENTIALS",
            tags = listOf("data structures", "array", "linked list", "tree", "graph", "hash table", "stack", "queue"),
            content = """
                💻 **Fundamental Data Structures:**
                
                • **Array**: Contiguous memory, O(1) random access by index, O(n) insert/delete.
                • **Linked List**: Dynamic nodes with pointers, O(1) insert at head, O(n) traversal.
                • **Hash Table / Map**: Key-value pairs using hash functions, average O(1) lookup, insertion, and deletion.
                • **Stack & Queue**: LIFO (Last In First Out) vs FIFO (First In First Out).
                • **Binary Search Tree (BST)**: Ordered tree with left < root < right. Balanced trees (AVL, Red-Black) guarantee O(log n) operations.
                • **Graph**: Nodes and edges representing complex relationships (directed, undirected, weighted).
            """.trimIndent()
        ),
        KnowledgeEntry(
            title = "CORE ALGORITHMS & TIME COMPLEXITY",
            tags = listOf("algorithm", "time complexity", "big o", "sorting", "binary search", "dijkstra", "dynamic programming"),
            content = """
                ⚡ **Algorithms & Big-O Notation:**
                
                • **Big-O Cheatsheet**:
                  - Constant: `O(1)` – Hash table access, array indexing.
                  - Logarithmic: `O(log n)` – Binary search in sorted array.
                  - Linear: `O(n)` – Iterating through a list.
                  - Linearithmic: `O(n log n)` – QuickSort, MergeSort, HeapSort.
                  - Quadratic: `O(n²)` – Nested loops, BubbleSort.
                • **Binary Search**: Divides search range in half each step: requires sorted data.
                • **Dijkstra's Algorithm**: Finds the shortest path in a non-negative weighted graph using priority queues.
                • **Dynamic Programming (DP)**: Solves problems by breaking them into overlapping subproblems and memoizing results (e.g. Knapsack, Fibonacci).
            """.trimIndent()
        ),
        KnowledgeEntry(
            title = "DATABASES: SQL VS NOSQL",
            tags = listOf("sql", "nosql", "database", "sqlite", "room", "postgres", "mongodb", "acid"),
            content = """
                🗄️ **Relational (SQL) vs Document (NoSQL) Databases:**
                
                • **SQL (Relational - PostgreSQL, MySQL, SQLite / Android Room)**:
                  - Structured tables, rigid schemas, primary & foreign keys.
                  - **ACID Guarantees**: Atomicity, Consistency, Isolation, Durability.
                  - Ideal for financial transactions, document indexes, and relational records.
                • **NoSQL (Document/Key-Value - MongoDB, Redis, Cassandra)**:
                  - Dynamic JSON schemas, horizontal scalability (sharding).
                  - **BASE Model**: Basically Available, Soft state, Eventual consistency.
                  - Ideal for caching, real-time analytics, and rapidly evolving unstructured data.
            """.trimIndent()
        ),
        KnowledgeEntry(
            title = "NETWORKING & WEB APIS (HTTP / REST / DNS)",
            tags = listOf("http", "https", "rest", "api", "dns", "tcp", "ip", "ssl", "tls", "network"),
            content = """
                🌐 **Computer Networking & Web APIs:**
                
                • **HTTP / HTTPS**: Application-level protocol. HTTPS uses TLS encryption to protect packets in transit on port 443.
                • **RESTful Architecture**: Uses standard HTTP verbs:
                  - `GET`: Read resource (idempotent, safe).
                  - `POST`: Create new resource.
                  - `PUT` / `PATCH`: Update resource.
                  - `DELETE`: Remove resource.
                • **HTTP Status Codes**:
                  - `200 OK`: Success.
                  - `400 Bad Request`: Client error in payload.
                  - `401 Unauthorized` / `403 Forbidden`: Authentication / permission issue.
                  - `404 Not Found`: Resource or endpoint does not exist.
                  - `500 Internal Server Error`: Server-side failure.
            """.trimIndent()
        ),

        // ── 8. Artificial Intelligence & Machine Learning ────────────────────
        KnowledgeEntry(
            title = "ON-DEVICE AI VS CLOUD AI",
            tags = listOf("on device ai", "cloud ai", "edge ai", "npu", "quantization", "offline ai", "gemini nano"),
            content = """
                🤖 **On-Device (Edge) AI vs Cloud AI:**
                
                • **On-Device AI (DOCSPHERE Offline Engine / Gemini Nano)**:
                  - **Zero Cloud Dependence**: 100% functional without internet or cell connectivity.
                  - **Total Privacy**: Sensitive documents and queries never leave the phone's RAM.
                  - **Low Latency & Zero Cost**: Eliminates API token bills and network lag.
                  - **Model Optimization**: Employs INT8/INT4 quantization and NPU silicon acceleration.
                • **Cloud AI (Gemini 3.8 Flash / Cloud LLMs)**:
                  - Massive multi-billion parameter neural networks.
                  - Exceptional general world knowledge, complex multi-lingual translation, and coding synthesis.
                • **DOCSPHERE Dual Architecture**: Blends fast on-device RAG for instant local files with cloud Gemini for comprehensive world intelligence!
            """.trimIndent()
        ),
        KnowledgeEntry(
            title = "RETRIEVAL-AUGMENTED GENERATION (RAG)",
            tags = listOf("rag", "retrieval augmented generation", "vector", "embeddings", "context", "search"),
            content = """
                🧠 **Retrieval-Augmented Generation (RAG):**
                
                • **The Problem**: Language models can hallucinate or lack access to your private, real-time local files.
                • **The RAG Solution**:
                  1. **Ingestion**: Documents are parsed, stripped into text chunks, and stored in a local database (SQLite/Room).
                  2. **Retrieval**: When you ask a question, the engine searches the indexed text to find the most relevant document passages.
                  3. **Augmentation**: The query is bundled together with the retrieved document snippets as context.
                  4. **Generation**: The AI answers based strictly on the retrieved facts, citing exact sources!
            """.trimIndent()
        ),
        KnowledgeEntry(
            title = "LARGE LANGUAGE MODELS (LLMS) & TRANSFORMERS",
            tags = listOf("llm", "transformer", "attention", "tokens", "prompt engineering", "hallucination"),
            content = """
                ✨ **How Large Language Models (LLMs) Work:**
                
                • **Transformer Architecture**: Uses self-attention mechanisms (Vaswani et al., 2017) to weigh relationships between all words in a sentence simultaneously.
                • **Tokens**: Words and subwords converted into mathematical vectors (embeddings). 1,000 tokens ≈ 750 English words.
                • **Prompt Engineering Tips**:
                  - Be specific: Specify output format (bullets, table, JSON).
                  - Provide context: Give examples or relevant document excerpts.
                  - Assign a role: *"Act as an expert financial auditor..."*.
            """.trimIndent()
        ),

        // ── 9. Cyber Security & Privacy ──────────────────────────────────────
        KnowledgeEntry(
            title = "CYBERSECURITY ESSENTIALS (PHISHING / 2FA / ZERO TRUST)",
            tags = listOf("cybersecurity", "phishing", "2fa", "mfa", "ransomware", "zero trust", "malware", "firewall"),
            content = """
                🛡️ **Cybersecurity Best Practices:**
                
                • **Phishing Prevention**: Never click links in unsolicited emails asking for credentials. Always verify sender domain authenticity.
                • **Two-Factor Authentication (2FA/MFA)**: Protect accounts with hardware keys (YubiKey) or authenticator apps (TOTP) instead of SMS.
                • **Zero Trust Model**: *"Never trust, always verify"*. Requires identity authentication and device posture verification for every single request.
                • **Ransomware Defense**: Maintain immutable offline 3-2-1 backups (3 copies, 2 different media types, 1 stored off-site).
            """.trimIndent()
        ),

        // ── 10. Productivity & Utilities ─────────────────────────────────────
        KnowledgeEntry(
            title = "TIME MANAGEMENT & PRODUCTIVITY FRAMEWORKS",
            tags = listOf("pomodoro", "eisenhower matrix", "productivity", "smart goals", "kanban", "focus"),
            content = """
                ⏱️ **Top Productivity Frameworks:**
                
                • **Pomodoro Technique**: 25 minutes of uninterrupted deep focus, followed by a 5-minute break. After 4 cycles, take a 20-minute break.
                • **Eisenhower Matrix**: Categorize tasks into 4 quadrants:
                  1. *Urgent & Important*: Do immediately.
                  2. *Important, Not Urgent*: Schedule on your calendar (highest value work).
                  3. *Urgent, Not Important*: Delegate or automate.
                  4. *Not Urgent & Not Important*: Eliminate.
                • **SMART Goals**: Specific, Measurable, Achievable, Relevant, and Time-bound.
            """.trimIndent()
        ),
        KnowledgeEntry(
            title = "DATA STORAGE & DIGITAL UNITS CONVERSION",
            tags = listOf("byte", "kilobyte", "megabyte", "gigabyte", "terabyte", "conversion", "units", "kb", "mb", "gb", "tb"),
            content = """
                💾 **Digital Storage Units (Binary vs Decimal):**
                
                • **Bits & Bytes**: `1 Byte (B) = 8 Bits (b)`.
                • **Binary Prefixes (IEC standard)**:
                  - `1 KiB (Kibibyte) = 1,024 Bytes`
                  - `1 MiB (Mebibyte) = 1,024 KiB = 1,048,576 Bytes`
                  - `1 GiB (Gibibyte) = 1,024 MiB`
                  - `1 TiB (Tebibyte) = 1,024 GiB`
                • **Storage Comparison**:
                  - Plain text document: ~5 KB – 50 KB
                  - Standard PDF report: ~500 KB – 5 MB
                  - High-res photo: ~3 MB – 10 MB
            """.trimIndent()
        ),

        // ── 11. Mobile Development & Android Architecture ────────────────────
        KnowledgeEntry(
            title = "ANDROID & JETPACK COMPOSE ARCHITECTURE",
            tags = listOf("android", "compose", "jetpack compose", "mobile", "viewmodel", "state", "hilt", "lifecycle"),
            content = """
                📱 **Android Development & Jetpack Compose:**
                
                • **Declarative UI**: Jetpack Compose simplifies UI development using reactive `@Composable` functions that automatically recompose when state changes.
                • **Recommended Architecture (Google Guide to App Architecture)**:
                  1. **UI Layer**: Compose screens observing `StateFlow` from ViewModels.
                  2. **Domain Layer**: Optional use cases handling complex business logic.
                  3. **Data Layer**: Repositories abstracting local storage (Room) and network (Retrofit).
                • **Key Architecture Components**:
                  - **Hilt**: Dependency injection for clean lifecycle management.
                  - **Room**: SQLite object mapping with compile-time query verification.
                  - **Coroutines & Flow**: Structured concurrency for non-blocking I/O operations.
            """.trimIndent()
        ),
        KnowledgeEntry(
            title = "KOTLIN PROGRAMMING & COROUTINES",
            tags = listOf("kotlin", "coroutines", "flow", "suspend", "dispatchers", "val", "var", "lambda"),
            content = """
                ⚡ **Kotlin Language & Asynchronous Coroutines:**
                
                • **Modern Features**: Concise syntax, null safety (`val name: String?`), extension functions, data classes, and sealed interfaces.
                • **Coroutines (Lightweight Threads)**:
                  - Suspending functions (`suspend fun`) pause execution without blocking OS threads.
                  - **Dispatchers.Main**: UI operations and Compose state updates.
                  - **Dispatchers.IO**: Disk reads, database queries, and network API calls.
                  - **Dispatchers.Default**: CPU-intensive parsing and mathematical calculations.
                • **Kotlin Flow**: Reactive asynchronous cold streams that emit multiple sequentially calculated values (`flow { emit(value) }`).
            """.trimIndent()
        ),
        KnowledgeEntry(
            title = "PYTHON & DATA SCIENCE ESSENTIALS",
            tags = listOf("python", "numpy", "pandas", "data science", "script", "django", "fastapi"),
            content = """
                🐍 **Python Programming & Scientific Computing:**
                
                • **Core Strengths**: Clean readable syntax, dynamic typing, vast ecosystem for AI, automation, and backend web APIs.
                • **Key Frameworks**:
                  - **FastAPI / Flask**: High-performance asynchronous REST microservices.
                  - **NumPy & Pandas**: Vectorized matrix calculations and tabular DataFrame manipulation.
                  - **PyTorch & TensorFlow**: Deep learning neural network training and inference.
                • **Best Practices**: Use virtual environments (`venv`), type hints (`def process(data: str) -> bool:`), and PEP 8 style formatting.
            """.trimIndent()
        ),
        KnowledgeEntry(
            title = "GIT & VERSION CONTROL WORKFLOWS",
            tags = listOf("git", "github", "commit", "branch", "merge", "rebase", "pull request", "repo"),
            content = """
                🐙 **Git & Collaborative Version Control:**
                
                • **Core Commands**:
                  - `git status` / `git diff`: Inspect working directory changes.
                  - `git add .` && `git commit -m "feat: description"`: Snapshot staged changes.
                  - `git branch -M main` && `git push -u origin main`: Publish to remote repository.
                • **Merge vs Rebase**:
                  - **Merge**: Preserves complete history branch graph with a merge commit.
                  - **Rebase**: Replays feature commits onto target branch for a clean, linear history.
                • **Conventional Commits**: Use semantic prefixes (`feat:`, `fix:`, `docs:`, `refactor:`, `perf:`).
            """.trimIndent()
        ),
        KnowledgeEntry(
            title = "DATA STRUCTURES & ALGORITHMS",
            tags = listOf("data structures", "algorithms", "dsa", "binary search", "hashmap", "tree", "graph", "big o"),
            content = """
                📊 **Data Structures & Algorithmic Complexity:**
                
                • **Time Complexity (Big-O Notation)**:
                  - `O(1)`: Constant time (Hash table lookup, array index access).
                  - `O(log n)`: Logarithmic time (Binary search on sorted arrays).
                  - `O(n)`: Linear time (Iterating through an unsorted list).
                  - `O(n log n)`: Quicksort, Mergesort, and Heapsort.
                  - `O(n²)`: Nested loops (Bubble sort, insertion sort).
                • **Essential Data Structures**:
                  - **HashMap / HashSet**: `O(1)` average lookup and insertion.
                  - **Trie (Prefix Tree)**: Ideal for autocomplete and document search indexing.
                  - **B-Trees**: The foundation of database indexes (SQLite, PostgreSQL).
            """.trimIndent()
        ),
        KnowledgeEntry(
            title = "SYSTEM DESIGN & DATABASE ARCHITECTURE",
            tags = listOf("system design", "database", "sql", "nosql", "postgres", "sqlite", "indexing", "sharding", "cache"),
            content = """
                🏛️ **System Design & Database Architectures:**
                
                • **SQL vs NoSQL**:
                  - **SQL (Relational / ACID)**: Best for structured data requiring transactional guarantees (PostgreSQL, MySQL, SQLite).
                  - **NoSQL (Document / Key-Value)**: Scalable horizontal partitioning for semi-structured JSON documents (MongoDB, Redis, Cassandra).
                  - **Indexing**: B-Tree and Hash indexes speed up `WHERE` queries from `O(n)` to `O(log n)` at the cost of slower writes.
                • **High-Availability Patterns**:
                  - **Caching (Redis / In-Memory)**: Reduces database load by 80%+ for frequently read items.
                  - **Load Balancing**: Distributes traffic across instances using round-robin or least-connections.
            """.trimIndent()
        )
    )

    // ── Conversational Checkers ──────────────────────────────────────────────

    fun isGreeting(text: String): Boolean {
        val clean = text.trim().lowercase(Locale.ROOT)
        val greetings = setOf(
            "hi", "hii", "hiii", "hiiii", "hello", "helloo", "hey", "heyy", "heya",
            "hola", "namaste", "vanakkam", "greetings", "good morning", "good afternoon",
            "good evening", "good day", "what's up", "whats up", "sup", "howdy", "yo"
        )
        if (clean in greetings) return true
        return greetings.any { clean.startsWith("$it ") || clean.endsWith(" $it") }
    }

    fun buildGreetingResponse(): String =
        "Hello! How can I help you today? If you're looking for specific files or need help summarizing documents in Google Drive, feel free to let me know!"

    fun isIdentityOrHelp(text: String): Boolean {
        val clean = text.trim().lowercase(Locale.ROOT)
        val patterns = listOf(
            "who are you", "what are you", "what is docsphere", "what can you do",
            "help", "how to use", "commands", "features", "how do you work",
            "tell me about yourself", "your name", "drive ai", "gemini"
        )
        return patterns.any { clean.contains(it) }
    }

    fun buildIdentityResponse(): String = buildString {
        appendLine("I am **DOCSPHERE AI**, modeled after Google Drive Gemini AI.")
        appendLine()
        appendLine("I help you search across your files, summarize documents, answer targeted questions, and extract key information with full privacy.")
    }

    fun isHowDoYouKnow(text: String): Boolean {
        val clean = text.trim().lowercase(Locale.ROOT)
        return listOf(
            "how you know", "how do you know", "where did you know",
            "how do you know my name", "who told you", "how you know my name"
        ).any { clean.contains(it) }
    }

    fun buildHowDoYouKnowResponse(): String =
        "I know your name from your user profile context and the documents in your library.\n❶ ❷ ❸\n\nWould you like me to help you find or review any of your documents or emails?"

    fun isUserIntroductionOrName(text: String): Boolean {
        val clean = text.trim().lowercase(Locale.ROOT)
        val patterns = listOf(
            "my name is", "my name", "what is my name", "what's my name",
            "who am i", "do you know my name", "tell me my name", "i'm ", "i am "
        )
        return patterns.any { clean.startsWith(it) || clean.contains(it) }
    }

    fun buildNameResponse(text: String, resolvedName: String?): String {
        val clean = text.trim()
        val lower = clean.lowercase(Locale.ROOT)

        val nameCandidate = when {
            lower.startsWith("my name is") -> clean.substringAfter("is", "").trim().trim('.', '!', '?', ',')
            lower.startsWith("call me") -> clean.substringAfter("me", "").trim().trim('.', '!', '?', ',')
            lower.startsWith("i am called") -> clean.substringAfter("called", "").trim().trim('.', '!', '?', ',')
            lower.startsWith("i'm ") -> clean.substringAfter("i'm", "").trim().trim('.', '!', '?', ',')
            lower.startsWith("i am ") -> clean.substringAfter("am", "").trim().trim('.', '!', '?', ',')
            else -> ""
        }

        if (nameCandidate.isNotBlank() && nameCandidate.length in 2..40 && !lower.contains("?")) {
            val capitalized = nameCandidate.split(" ")
                .joinToString(" ") { w -> w.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() } }
            return "Nice to meet you, $capitalized! I've updated your profile.\n\nIs there anything else I can help you with today?"
        }

        // If user is asking "my name is??" or "what is my name?" or "who am i"
        return if (!resolvedName.isNullOrBlank()) {
            "Your name is $resolvedName.\n\nIs there anything else I can help you with today?"
        } else {
            "I don't have your name in your profile or indexed documents yet. What would you like me to call you?"
        }
    }

    fun isSocialOrPolite(text: String): Boolean {
        val clean = text.trim().lowercase(Locale.ROOT)
        val words = setOf(
            "thank you", "thanks", "thx", "good", "great", "awesome", "cool",
            "nice", "bye", "goodbye", "see you", "how are you", "who made you",
            "tell me a joke", "joke"
        )
        return words.any { clean.contains(it) }
    }

    fun buildSocialResponse(text: String): String {
        val clean = text.trim().lowercase(Locale.ROOT)
        return when {
            clean.contains("joke") -> {
                "😄 **Document Joke:**\n\nWhy did the PDF cross the road?\n\n*To get to the other platform without losing its formatting!* 📄"
            }
            clean.contains("how are you") -> {
                "I'm running at full speed on your phone's processor! ⚡ Ready to explore and analyze your documents."
            }
            clean.contains("who made you") -> {
                "I was built for **DOCSPHERE** to bring secure, private on-device document intelligence to your smartphone."
            }
            clean.contains("bye") || clean.contains("see you") -> {
                "Goodbye! 👋 Have a wonderful day. I'm always here whenever you need to search or summarize your files."
            }
            clean.contains("thank") -> {
                "You're very welcome! 😊 Let me know if you need anything else from your documents."
            }
            else -> {
                "Glad I could assist! 👍 Let me know if you have more questions about your files or need another summary."
            }
        }
    }

    // ── Multi-Keyword Knowledge Search ───────────────────────────────────────

    fun searchKnowledge(query: String): String? {
        val clean = query.trim().lowercase(Locale.ROOT)
        val words = clean.split(Regex("[^a-zA-Z0-9]+")).filter { it.length > 2 }
        if (words.isEmpty()) return null

        var bestMatch: KnowledgeEntry? = null
        var maxScore = 0

        for (entry in knowledgeDataset) {
            var score = 0
            for (tag in entry.tags) {
                if (clean.contains(tag)) score += 6
                for (w in words) {
                    if (tag.contains(w)) score += 2
                }
            }
            if (score > maxScore && score >= 3) {
                maxScore = score
                bestMatch = entry
            }
        }

        if (bestMatch != null) {
            return bestMatch.content
        }

        return null
    }

    // ── Math & Units Evaluator ───────────────────────────────────────────────

    fun tryEvaluateMath(expr: String): String? {
        val clean = expr.replace("x", "*").replace("X", "*").trim()
        val matcher = Pattern.compile("^(\\d+(?:\\.\\d+)?)\\s*([+\\-*/%^])\\s*(\\d+(?:\\.\\d+)?)$").matcher(clean)
        if (matcher.matches()) {
            val a = matcher.group(1)?.toDoubleOrNull() ?: return null
            val op = matcher.group(2) ?: return null
            val b = matcher.group(3)?.toDoubleOrNull() ?: return null
            val result = when (op) {
                "+" -> a + b
                "-" -> a - b
                "*" -> a * b
                "/" -> if (b != 0.0) a / b else return "Division by zero is undefined."
                "%" -> a % b
                "^" -> Math.pow(a, b)
                else -> return null
            }
            val formatted = if (result % 1.0 == 0.0) result.toLong().toString() else "%.4f".format(result).trimEnd('0').trimEnd('.')
            return "🧮 **Calculation Result:**\n\n> `$expr` = **$formatted**"
        }
        return null
    }
}
