#!/data/data/com.termux/files/usr/bin/python3
# tts_sanitize.py - Clean text for Text-to-Speech playback
import sys
import re

def strip_history(text, history):
    text_clean = text.strip()
    hist_clean = history.strip()
    if not hist_clean:
        return text_clean
        
    if text_clean.startswith(hist_clean):
        return text_clean[len(hist_clean):].strip()
        
    norm_text = re.sub(r'\s+', ' ', text_clean)
    norm_hist = re.sub(r'\s+', ' ', hist_clean)
    if norm_text.startswith(norm_hist):
        hist_words = norm_hist.split()
        idx = 0
        for word in hist_words:
            found_idx = text_clean.find(word, idx)
            if found_idx == -1:
                return text_clean
            idx = found_idx + len(word)
        return text_clean[idx:].strip()
            
    return text_clean

def sanitize_for_tts(text):
    # 1. Clean markdown headers and formatting
    # Headers like #, ##, etc. at the start of a line
    text = re.sub(r'(?m)^#+\s+', '', text)
    # Bold / Italic formatting
    text = re.sub(r'\*\*([^*]+)\*\*|__([^_]+)__', r'\1\2', text)
    text = re.sub(r'\*([^*]+)\*|_([^_]+)_', r'\1\2', text)
    # Markdown links [text](url) -> text
    text = re.sub(r'\[([^\]]+)\]\([^)]+\)', r'\1', text)
    # Remove markdown inline code backticks `code` -> code
    text = re.sub(r'`([^`]+)`', r'\1', text)
    # Remove markdown code blocks tags (keep the code content, remove the ```lang tags)
    text = re.sub(r'```[a-zA-Z0-9_-]*\s*', '', text)
    
    # 2. Handle slashes and paths
    # Replace all slashes (forward and backward) with ' slash ' or ' backslash '
    text = re.sub(r'/', ' slash ', text)
    text = re.sub(r'\\', ' backslash ', text)

    # 3. Handle dots in filenames, paths, domains, etc.
    # Convert dots in the middle of words to ' dot ' (e.g. file.txt)
    text = re.sub(r'(?<=\w)\.(?=\w)', ' dot ', text)
    # Convert leading dots (e.g. .bashrc) to ' dot '
    text = re.sub(r'(^|\s)\.([a-zA-Z0-9_-]+)', r'\1dot \2', text)

    # 4. Handle other special characters and symbols
    # Replace currencies: $100 -> 100 dollars, $100.50 -> 100.50 dollars (which becomes 100 dot 50 dollars)
    text = re.sub(r'\$(\d+(?:\.\d+)?)', r'\1 dollars', text)
    # Fallback for standalone $
    text = re.sub(r'\$', ' dollars ', text)
    
    text = re.sub(r'&', ' and ', text)
    text = re.sub(r'@', ' at ', text)
    text = re.sub(r'%', ' percent', text)
    text = re.sub(r'\+', ' plus ', text)
    text = re.sub(r'=', ' equals ', text)
    text = re.sub(r'#', ' number ', text)
    text = re.sub(r'~', ' tilde ', text)
    
    # Replace underscore with space so words are spoken separately
    text = re.sub(r'_', ' ', text)

    # 5. Remove emojis and other non-grammar/non-pronounceable special characters
    # We keep: letters/numbers (\w), whitespace (\s), and standard punctuation: . , ! ? ; : ( ) - ' " ¿ ¡
    text = re.sub(r'[^\w\s.,!?;:\-\'"()¿¡]', '', text)
    
    # 6. Clean up extra whitespace
    # Replace multiple spaces/tabs with a single space
    text = re.sub(r'[ \t]+', ' ', text)
    # Collapse multiple newlines into a single space (TTS should flow continuously, maybe with short pauses)
    text = re.sub(r'\n+', ' ', text)
    
    return text.strip()

if __name__ == '__main__':
    history_file = None
    history_only = False
    args = sys.argv[1:]
    
    clean_args = []
    i = 0
    while i < len(args):
        if args[i] == '--history-file' and i + 1 < len(args):
            history_file = args[i+1]
            i += 2
        elif args[i] == '--history-only':
            history_only = True
            i += 1
        else:
            clean_args.append(args[i])
            i += 1
            
    if clean_args:
        input_text = " ".join(clean_args)
    else:
        input_text = sys.stdin.read()
        
    if history_file:
        try:
            with open(history_file, 'r', encoding='utf-8') as f:
                history_content = f.read()
            input_text = strip_history(input_text, history_content)
        except Exception:
            pass
    
    if history_only:
        sys.stdout.write(input_text)
    else:
        sys.stdout.write(sanitize_for_tts(input_text))
