#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <limits.h>
#include "mobi.h"
#include "index.h"
#include "structure.h"

/* 上游内部词形函数；固定版本与本目录来源说明对应。 */
extern MOBI_RET mobi_reconstruct_infl(char *, const MOBIIndx *, const MOBIIndexEntry *);
extern MOBI_RET mobi_reconstruct_infl_v1(char *, MOBITrie *, const MOBIIndexEntry *);

static int u32(FILE *out, uint32_t n) {
    unsigned char data[4] = {n >> 24, n >> 16, n >> 8, n};
    return fwrite(data, 1, 4, out) == 4;
}

static int string(FILE *out, const char *text) {
    size_t size = text ? strlen(text) : 0;
    return size <= UINT32_MAX && u32(out, (uint32_t) size) &&
        (!size || fwrite(text, 1, size, out) == size);
}

static int compare_offset(const void *a, const void *b) {
    uint32_t x = *(const uint32_t *) a, y = *(const uint32_t *) b;
    return (x > y) - (x < y);
}

/* 汉语词典的标签 22 指向同一词典中的原始词条；限制循环及跳转深度。 */
static const MOBIIndexEntry *resolve_orth(const MOBIIndx *orth, size_t index) {
    size_t visited[16];
    for (size_t depth = 0; depth < 16; depth++) {
        if (index >= orth->entries_count) return NULL;
        for (size_t i = 0; i < depth; i++) if (visited[i] == index) return NULL;
        visited[depth] = index;
        const MOBIIndexEntry *entry = &orth->entries[index];
        if (mobi_get_orth_entry_offset(entry) != MOBI_NOTSET) return entry;
        uint32_t *targets = NULL;
        if (mobi_get_indxentry_tagarray(&targets, entry, 22) != 1) return NULL;
        index = targets[0];
    }
    return NULL;
}

static int write_file(const char *dir, const char *name, const void *bytes, size_t size) {
    char path[PATH_MAX];
    int length = snprintf(path, sizeof(path), "%s/%s", dir, name);
    if (length < 0 || (size_t) length >= sizeof(path)) return 0;
    FILE *out = fopen(path, "wb");
    if (!out) return 0;
    int ok = fwrite(bytes, 1, size, out) == size;
    if (fclose(out)) ok = 0;
    return ok;
}

/* 返回值：0 成功；1 损坏；2 非词典；3 受保护；4 限额；5 文件错误。 */
int legado_mobi_extract(const char *path, const char *dir, size_t max_text) {
    int result = 1;
    MOBIData *m = mobi_init();
    MOBIRawml *raw = NULL;
    MOBITrie *trie = NULL;
    uint32_t *offsets = NULL;
    FILE *out = NULL;
    FILE *file = fopen(path, "rb");
    if (!m || !file) { result = 5; goto finish; }
    /* 必须在读取前选择旧格式部分，避免混合文件自动交换至 KF8。 */
    m->use_kf8 = false;
    if (mobi_load_file(m, file) != MOBI_SUCCESS) goto finish;
    fclose(file); file = NULL;
    if (mobi_is_encrypted(m)) { result = 3; goto finish; }
    if (!mobi_is_dictionary(m)) { result = 2; goto finish; }
    if (mobi_get_text_maxsize(m) > max_text) { result = 4; goto finish; }
    raw = mobi_init_rawml(m);
    if (!raw || mobi_parse_rawml_opt(raw, m, false, true, false) != MOBI_SUCCESS) goto finish;
    if (!raw->orth || !raw->flow || raw->flow->type != T_HTML || !raw->orth->entries_count) goto finish;
    size_t count = raw->orth->entries_count;
    if (count > 5000000 || raw->flow->size > max_text) { result = 4; goto finish; }
    offsets = malloc((count + 1) * sizeof(uint32_t));
    if (!offsets) { result = 4; goto finish; }
    for (size_t i = 0; i < count; i++) {
        const MOBIIndexEntry *entry = resolve_orth(raw->orth, i);
        if (!entry) goto finish;
        offsets[i] = mobi_get_orth_entry_offset(entry);
        if (offsets[i] == MOBI_NOTSET || offsets[i] >= raw->flow->size) goto finish;
    }
    offsets[count] = (uint32_t) raw->flow->size;
    qsort(offsets, count + 1, sizeof(uint32_t), compare_offset);
    bool infl2 = raw->infl && mobi_indx_has_tag(raw->orth, INDX_TAGARR_ORTH_INFL);
    bool infl1 = raw->infl && !infl2 && mobi_indx_has_tag(raw->infl, INDX_TAGARR_INFL_PARTS_V1);
    if (raw->infl && !infl2 && !infl1) { result = 2; goto finish; }
    if (infl1) {
        for (size_t i = 0; i < raw->infl->entries_count; i++) {
            if (mobi_trie_insert_infl(&trie, raw->infl, i) != MOBI_SUCCESS) goto finish;
        }
    }
    char output_path[PATH_MAX];
    if (snprintf(output_path, sizeof(output_path), "%s/mobi.index", dir) >= (int) sizeof(output_path)) goto finish;
    out = fopen(output_path, "wb");
    if (!out) { result = 5; goto finish; }
    char *title = mobi_meta_get_title(m);
    uint32_t encoding = m->mh && m->mh->text_encoding ? *m->mh->text_encoding : 65001;
    const char *input_language = m->mh && m->mh->dict_input_lang ? mobi_get_locale_string(*m->mh->dict_input_lang) : NULL;
    const char *output_language = m->mh && m->mh->dict_output_lang ? mobi_get_locale_string(*m->mh->dict_output_lang) : NULL;
    int header_ok = u32(out, 0x4c444d49) && u32(out, encoding) && string(out, title) && u32(out, (uint32_t) count) &&
        string(out, input_language) && string(out, output_language) && u32(out, raw->orth->encoding) &&
        u32(out, raw->infl ? raw->infl->encoding : raw->orth->encoding);
    free(title);
    if (!header_ok) { result = 5; goto finish; }
    /* 先写正文词条，再写别名，保证磁盘索引中的别名目标已存在。 */
    for (int aliases = 0; aliases < 2; aliases++) for (size_t i = 0; i < count; i++) {
        MOBIIndexEntry *entry = &raw->orth->entries[i];
        const bool alias = mobi_get_orth_entry_offset(entry) == MOBI_NOTSET;
        if (alias != (aliases != 0)) continue;
        const MOBIIndexEntry *target = resolve_orth(raw->orth, i);
        if (!target) goto finish;
        uint32_t start = mobi_get_orth_entry_offset(target);
        if (alias) {
            if (!u32(out, start) || !u32(out, 0) || !string(out, entry->label) || !string(out, "")) goto finish;
            continue;
        }
        uint32_t length = mobi_get_orth_entry_length(entry);
        if (!length || length == MOBI_NOTSET) {
            size_t lo = 0, hi = count + 1;
            while (lo < hi) {
                size_t mid = lo + (hi - lo) / 2;
                if (offsets[mid] <= start) lo = mid + 1; else hi = mid;
            }
            if (lo > count) goto finish;
            length = offsets[lo] - start;
        }
        if (length > 16777216 || length > raw->flow->size - start) goto finish;
        char *infl = calloc(INDX_INFLTAG_SIZEMAX + 1, 1);
        if (!infl) { result = 4; goto finish; }
        MOBI_RET ret = MOBI_SUCCESS;
        if (infl2) ret = mobi_reconstruct_infl(infl, raw->infl, entry);
        else if (infl1) ret = mobi_reconstruct_infl_v1(infl, trie, entry);
        int ok = ret == MOBI_SUCCESS && u32(out, start) && u32(out, length) && string(out, entry->label) && string(out, infl);
        free(infl);
        if (!ok) goto finish;
    }
    if (fclose(out)) { out = NULL; result = 5; goto finish; }
    out = NULL;
    if (!write_file(dir, "mobi.body", raw->flow->data, raw->flow->size)) { result = 5; goto finish; }
    for (MOBIPart *part = raw->flow->next; part; part = part->next) {
        if (part->size > 16777216) { result = 4; goto finish; }
        MOBIFileMeta meta = mobi_get_filemeta_by_type(part->type);
        if (!meta.extension[0]) continue;
        char name[64];
        snprintf(name, sizeof(name), "flow%05zu.%s", part->uid, meta.extension);
        if (!write_file(dir, name, part->data, part->size)) { result = 5; goto finish; }
    }
    for (MOBIPart *part = raw->resources; part; part = part->next) {
        if (part->size > 16777216) { result = 4; goto finish; }
        MOBIFileMeta meta = mobi_get_filemeta_by_type(part->type);
        if (!meta.extension[0]) continue;
        char name[64];
        snprintf(name, sizeof(name), "resource%05zu.%s", part->uid, meta.extension);
        if (!write_file(dir, name, part->data, part->size)) { result = 5; goto finish; }
    }
    result = 0;
finish:
    if (file) fclose(file);
    if (out) fclose(out);
    free(offsets);
    mobi_trie_free(trie);
    if (raw) mobi_free_rawml(raw);
    if (m) mobi_free(m);
    return result;
}

#ifdef __ANDROID__
#include <jni.h>
JNIEXPORT jint JNICALL Java_io_legado_app_model_dictionary_MobiNative_extract(
    JNIEnv *env, jobject instance, jbyteArray input, jbyteArray output, jlong limit) {
    (void) instance;
    jsize input_size = (*env)->GetArrayLength(env, input);
    jsize output_size = (*env)->GetArrayLength(env, output);
    if (input_size <= 0 || input_size >= PATH_MAX || output_size <= 0 || output_size >= PATH_MAX) return 5;
    char path[PATH_MAX] = {0}, dir[PATH_MAX] = {0};
    (*env)->GetByteArrayRegion(env, input, 0, input_size, (jbyte *) path);
    (*env)->GetByteArrayRegion(env, output, 0, output_size, (jbyte *) dir);
    if ((*env)->ExceptionCheck(env)) return 5;
    return legado_mobi_extract(path, dir, (size_t) limit);
}
#endif
