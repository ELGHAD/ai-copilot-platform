import fitz  # pymupdf
from docx import Document
from pathlib import Path
import logging
import re

logger = logging.getLogger(__name__)


class DocumentParser:

    def parse(self, file_path: str, filename: str) -> list[dict]:
        """
        Parse un document PDF ou DOCX et retourne une liste de pages/sections
        avec leur contenu et métadonnées.
        """
        extension = Path(filename).suffix.lower()

        if extension == ".pdf":
            return self._parse_pdf(file_path, filename)
        elif extension == ".docx":
            return self._parse_docx(file_path, filename)
        else:
            raise ValueError(f"Format non supporté : {extension}. Utilisez PDF ou DOCX.")

    def _parse_pdf(self, file_path: str, filename: str) -> list[dict]:
        """
        Parse un PDF page par page avec pymupdf.
        Préserve la structure et nettoie le texte.
        """
        pages = []

        try:
            doc = fitz.open(file_path)

            for page_num in range(len(doc)):
                page = doc[page_num]

                # Extraction du texte avec préservation de la mise en page
                text = page.get_text("text")
                text = self._clean_text(text)

                if len(text.strip()) < 20:
                    # Page vide ou quasi-vide, on ignore
                    continue

                pages.append({
                    "content": text,
                    "metadata": {
                        "source": filename,
                        "page": page_num + 1,
                        "total_pages": len(doc),
                        "type": "pdf"
                    }
                })

            doc.close()
            logger.info(f"PDF parsé : {filename} — {len(pages)} pages extraites")

        except Exception as e:
            logger.error(f"Erreur parsing PDF {filename} : {e}")
            raise

        return pages

    def _parse_docx(self, file_path: str, filename: str) -> list[dict]:
        """
        Parse un DOCX en respectant la structure des titres et paragraphes.
        Regroupe les paragraphes en sections logiques.
        """
        sections = []

        try:
            doc = Document(file_path)
            current_section = []
            current_heading = ""
            section_index = 1

            for paragraph in doc.paragraphs:
                text = paragraph.text.strip()

                if not text:
                    continue

                # Détection des titres (Heading 1, 2, 3)
                if paragraph.style.name.startswith("Heading"):
                    # Sauvegarder la section précédente
                    if current_section:
                        content = self._clean_text("\n".join(current_section))
                        if len(content.strip()) >= 20:
                            sections.append({
                                "content": content,
                                "metadata": {
                                    "source": filename,
                                    "section": section_index,
                                    "heading": current_heading,
                                    "type": "docx"
                                }
                            })
                            section_index += 1

                    # Nouvelle section
                    current_heading = text
                    current_section = [text]
                else:
                    current_section.append(text)

            # Sauvegarder la dernière section
            if current_section:
                content = self._clean_text("\n".join(current_section))
                if len(content.strip()) >= 20:
                    sections.append({
                        "content": content,
                        "metadata": {
                            "source": filename,
                            "section": section_index,
                            "heading": current_heading,
                            "type": "docx"
                        }
                    })

            logger.info(f"DOCX parsé : {filename} — {len(sections)} sections extraites")

        except Exception as e:
            logger.error(f"Erreur parsing DOCX {filename} : {e}")
            raise

        return sections

    def _clean_text(self, text: str) -> str:
        """
        Nettoie le texte extrait :
        - Supprime les espaces multiples
        - Supprime les lignes vides excessives
        - Normalise les caractères spéciaux français
        """
        # Supprimer les caractères de contrôle sauf newlines
        text = re.sub(r'[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]', '', text)

        # Normaliser les espaces multiples sur une même ligne
        text = re.sub(r'[ \t]+', ' ', text)

        # Normaliser les lignes vides multiples (max 2)
        text = re.sub(r'\n{3,}', '\n\n', text)

        # Supprimer les tirets de coupure de mots (PDF)
        text = re.sub(r'(\w)-\n(\w)', r'\1\2', text)

        return text.strip()