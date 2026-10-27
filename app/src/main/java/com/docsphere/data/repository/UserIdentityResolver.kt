package com.docsphere.data.repository

import android.content.Context
import com.docsphere.data.local.DocumentDao
import com.docsphere.data.preferences.DocSpherePreferences
import com.google.android.gms.auth.api.signin.GoogleSignIn
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserIdentityResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: DocSpherePreferences,
    private val documentDao: DocumentDao
) {

    /**
     * Dynamically resolves the user's real identity name from:
     * 1. User preferences (saved name or self-introduction)
     * 2. Connected Google Account (Google Sign-In display name)
     * 3. Indexed Identity Documents (Aadhaar Card, Resume, PAN Card)
     * Returns null if no identity can be determined yet.
     */
    suspend fun resolveUserName(): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        // 1. Saved preference
        val saved = try { preferences.userName.first() } catch (_: Throwable) { null }
        if (!saved.isNullOrBlank()) {
            return@withContext saved.trim()
        }

        // 2. Google Account on the device
        val googleAccount = try { GoogleSignIn.getLastSignedInAccount(context) } catch (_: Throwable) { null }
        val googleName = googleAccount?.displayName
        if (!googleName.isNullOrBlank()) {
            val clean = cleanPersonName(googleName)
            preferences.setUserName(clean)
            return@withContext clean
        }

        // 3. Scan indexed documents for identity documents
        val docs = try { documentDao.getRecentWithText(30) } catch (_: Throwable) { emptyList() }
        for (doc in docs) {
            val text = doc.extractedText
            val nameInDoc = doc.displayName.lowercase(Locale.ROOT)

            // Check Aadhaar
            if (nameInDoc.contains("aadhaar") || nameInDoc.contains("aadhar") || text.contains("aadhaar", true) || text.contains("aadhar", true) || text.contains("uidai", true)) {
                val extracted = extractAadhaarName(text)
                if (extracted != null) {
                    preferences.setUserName(extracted)
                    return@withContext extracted
                }
            }

            // Check Resume / CV
            if (nameInDoc.contains("resume") || nameInDoc.contains("cv") || text.contains("curriculum vitae", true) || (text.contains("education", true) && text.contains("experience", true))) {
                val extracted = extractResumeName(text)
                if (extracted != null) {
                    preferences.setUserName(extracted)
                    return@withContext extracted
                }
            }

            // Check PAN Card
            if (nameInDoc.contains("pan") || text.contains("income tax department", true) || text.contains("permanent account number", true)) {
                val extracted = extractPanName(text)
                if (extracted != null) {
                    preferences.setUserName(extracted)
                    return@withContext extracted
                }
            }
        }

        return@withContext null
    }

    suspend fun saveUserName(name: String) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val clean = cleanPersonName(name)
        if (clean.isNotBlank()) {
            preferences.setUserName(clean)
        }
    }

    companion object {
        fun extractAadhaarName(text: String): String? {
            val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }

            // Pattern 1: Look for "To" or "To,"
            for (i in lines.indices) {
                val line = lines[i]
                if (line.equals("To", ignoreCase = true) || line.equals("To,", ignoreCase = true) || line.startsWith("To:", ignoreCase = true)) {
                    val candidate = if (line.contains(":") && line.substringAfter(":").trim().length in 2..40) {
                        line.substringAfter(":").trim()
                    } else if (i + 1 < lines.size) {
                        lines[i + 1]
                    } else null

                    val clean = candidate?.let { extractCleanPersonName(it) }
                    if (clean != null) return clean
                }
            }

            // Pattern 2: Look for line with "Name:" or "Name :" or "Name /"
            for (line in lines) {
                val lower = line.lowercase(Locale.ROOT)
                if (lower.startsWith("name:") || lower.startsWith("name :") || lower.startsWith("name /") || lower.startsWith("name/")) {
                    val candidate = line.substringAfter(":").substringAfter("/").trim()
                    val clean = extractCleanPersonName(candidate)
                    if (clean != null) return clean
                }
            }

            // Pattern 3: Look for line preceding "DOB" / "Date of Birth" / "Year of Birth" / Regional DOB
            for (i in lines.indices) {
                val lower = lines[i].lowercase(Locale.ROOT)
                if (lower.contains("dob") || lower.contains("date of birth") || lower.contains("year of birth") ||
                    lower.contains("जन्म तिथि") || lower.contains("பிறந்த தேதி") || lower.contains("పుట్టిన తేదీ") ||
                    lower.contains("ಜನನ ದಿನಾಂಕ") || lower.contains("ജനന തീയതി") || lower.contains("जन्म तारीख") ||
                    lower.contains("yob")) {
                    for (offset in 1..3) {
                        if (i - offset >= 0) {
                            val clean = extractCleanPersonName(lines[i - offset])
                            if (clean != null) return clean
                        }
                    }
                }
            }

            // Pattern 4: Line preceding Gender / Sex
            for (i in lines.indices) {
                val lower = lines[i].lowercase(Locale.ROOT)
                if (lower.contains("male") || lower.contains("female") || lower.contains("transgender") ||
                    lower.contains("gender") || lower.contains("ஆண்") || lower.contains("பெண்") ||
                    lower.contains("पुरुष") || lower.contains("महिला")) {
                    for (offset in 1..2) {
                        if (i - offset >= 0) {
                            val clean = extractCleanPersonName(lines[i - offset])
                            if (clean != null) return clean
                        }
                    }
                }
            }

            // Pattern 5: Line preceding S/O, D/O, W/O, C/O
            for (i in lines.indices) {
                val lower = lines[i].lowercase(Locale.ROOT)
                if (lower.startsWith("s/o") || lower.startsWith("d/o") || lower.startsWith("w/o") || lower.startsWith("c/o") ||
                    lower.contains("son of") || lower.contains("daughter of") || lower.contains("care of")) {
                    if (i - 1 >= 0) {
                        val clean = extractCleanPersonName(lines[i - 1])
                        if (clean != null) return clean
                    }
                }
            }

            // Pattern 6: Line between "Government of India" / "UIDAI" and "DOB"
            var passedGov = false
            for (line in lines) {
                val lower = line.lowercase(Locale.ROOT)
                if (lower.contains("government of india") || lower.contains("unique identification") ||
                    lower.contains("uidai") || lower.contains("भारत सरकार")) {
                    passedGov = true
                    continue
                }
                if (passedGov) {
                    if (lower.contains("dob") || lower.contains("date of birth") || lower.contains("enrollment") ||
                        lower.contains("enrolment") || lower.contains("download") || lower.contains("generation")) {
                        continue
                    }
                    val clean = extractCleanPersonName(line)
                    if (clean != null) return clean
                }
            }

            return null
        }

        fun extractResumeName(text: String): String? {
            val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
            for (line in lines.take(8)) {
                val clean = extractCleanPersonName(line)
                if (clean != null) return clean
            }
            return null
        }

        fun extractPanName(text: String): String? {
            val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
            for (i in lines.indices) {
                val lower = lines[i].lowercase(Locale.ROOT)
                if (lower.contains("name") && !lower.contains("father")) {
                    val candidate = lines[i].substringAfter(":", "").trim()
                    if (candidate.isNotBlank()) {
                        val clean = extractCleanPersonName(candidate)
                        if (clean != null) return clean
                    }
                    if (i + 1 < lines.size) {
                        val cleanNext = extractCleanPersonName(lines[i + 1])
                        if (cleanNext != null) return cleanNext
                    }
                }
            }
            return null
        }

        /**
         * Cleans, unbundles bilingual tokens (e.g. Tamil / English), strips prefixes,
         * and extracts valid Latin person names.
         */
        fun extractCleanPersonName(raw: String): String? {
            val trimmed = raw.trim()
            if (trimmed.isBlank()) return null

            // Handle bilingual slashes e.g. "கார்த்திகேயன் டி / Karthikeyan D"
            if (trimmed.contains("/")) {
                val parts = trimmed.split("/")
                for (part in parts) {
                    val candidate = extractCleanPersonName(part)
                    if (candidate != null) return candidate
                }
            }

            // Strip common honorifics & labels
            var cleaned = trimmed
                .replace(Regex("^(?:To[:,-]?|Name[:,-]?|Shri|Smt\\.?|Mr\\.?|Ms\\.?|Mrs\\.?|Dr\\.?)\\s+", RegexOption.IGNORE_CASE), "")
                .trim()

            // Extract Latin words
            val latinWords = Regex("[A-Za-z][A-Za-z.']{0,35}")
                .findAll(cleaned)
                .map { it.value.trimEnd('.') }
                .filter { it.isNotBlank() && !it.equals("To", ignoreCase = true) && !it.equals("Name", ignoreCase = true) }
                .toList()

            if (latinWords.size in 1..5) {
                val candidate = latinWords.joinToString(" ")
                if (isValidPersonName(candidate)) {
                    return cleanPersonName(candidate)
                }
            }

            return null
        }

        fun isValidPersonName(text: String): Boolean {
            val clean = text.trim()
            if (clean.length !in 3..45) return false
            val lower = clean.lowercase(Locale.ROOT)
            val nonNameWords = listOf(
                "government", "india", "authority", "unique", "identification", "uidai",
                "enrolment", "enrollment", "male", "female", "transgender", "address",
                "download", "generation", "date", "help@uidai", "www.uidai", "signature",
                "valid", "aadhaar", "aadhar", "card", "proof", "citizenship", "electronic",
                "qr code", "resident", "my aadhaar", "mera aadhaar", "income tax", "department",
                "curriculum", "vitae", "resume", "objective", "summary", "profile", "contact",
                "education", "skills", "experience", "projects", "phone", "email", "linkedin",
                "issue date", "print date", "director", "permanent", "account", "republic"
            )
            if (nonNameWords.any { lower.contains(it) }) return false
            if (!clean.matches(Regex("^[A-Za-z][A-Za-z .'-]{1,}$"))) return false
            val words = clean.split(Regex("\\s+")).filter { it.isNotBlank() }
            return words.size in 1..5
        }

        fun cleanPersonName(text: String): String {
            return text.trim().trim(',', '.', ':', '-', ';')
                .split(Regex("\\s+"))
                .joinToString(" ") { w ->
                    if (w.length == 1) w.uppercase(Locale.ROOT)
                    else w.lowercase(Locale.ROOT).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
                }
        }
    }
}
