import { labelDigest } from './matching-evidence.mjs';
import { gtinKey, isAmount, isLabel } from './price-provider.mjs';

export const rulesVersion = 'verified-food-1';
const normalize = name => (name ?? '').normalize('NFD').replace(/\p{M}/gu, '')
    .toLowerCase().replace(/[^\p{L}\p{N}]+/gu, ' ').trim();
const known = value => isLabel(value) && !['UNKNOWN', 'UNSPECIFIED', 'PROVISIONAL', 'AMBIGUOUS']
    .includes(value.trim().toUpperCase());
const list = values => Array.isArray(values) && values.every(known);

export function candidate(i, { product, package: pack, facts = {} }) {
    const tokens = new Set(normalize(product.name).split(' '));
    return normalize(i.name).split(' ').some(word => word.length > 1 && tokens.has(word))
        || (known(i.category) && i.category === facts.category)
        || (known(i.concept) && i.concept === facts.concept)
        || (gtinKey(i.target?.gtin) && gtinKey(i.target.gtin) === gtinKey(pack.gtin));
}

export function assess(i, { product, package: pack, facts = {} }, threshold) {
    const missing = [], conflicts = [], signals = [];
    function compare(field, left, right) {
        if (!known(left) || !known(right)) missing.push(field);
        else if (left !== right) conflicts.push(field);
        else signals.push(field);
    }
    if (!isLabel(i.canonicalId) || !isLabel(i.revision) || i.reviewed !== true || !['VISIBLE', 'SECONDARY'].includes(i.catalogState)) missing.push('canonicalReview');
    if (!isLabel(facts.sourceRef) || facts.reviewed !== true || facts.labelDigest !== labelDigest({ product, package: pack })) missing.push('labelReview');
    for (const field of ['category', 'concept', 'preparation', 'composition', 'form', 'basis', 'diet']) {
        compare(field, i[field], facts[field]);
    }
    if (!list(i.ingredients) || !i.ingredients.length || !list(facts.ingredients) || !facts.ingredients.length) {
        missing.push('ingredients');
    } else {
        compare('ingredients', JSON.stringify(i.ingredients), JSON.stringify(facts.ingredients));
    }
    if (!list(i.excludeAllergens)) missing.push('restrictions');
    else for (const allergen of i.excludeAllergens) {
        const status = facts.allergens?.[allergen];
        if (['CONTAINS', 'MAY_CONTAIN'].includes(status)) conflicts.push('allergen.' + allergen);
        else if (status !== 'FREE') missing.push('allergen.' + allergen);
    }

    const net = pack.netContent;
    const fixed = pack.weightMode === 'FIXED' && pack.saleUnit === 'PACKAGE' &&
        isAmount(net?.amount) && Number(net.amount) > 0 && !/\.\d{4}$/.test(net.amount) &&
        ['G', 'ML', 'UNIT'].includes(net.unit) && (net.unit !== 'UNIT' || Number.isInteger(Number(net.amount))) &&
        Number.isSafeInteger(pack.unitsPerPackage) && pack.unitsPerPackage > 0;
    const variable = pack.weightMode === 'VARIABLE' && pack.saleUnit === 'KG' &&
        net == null && pack.unitsPerPackage == null;
    if (!(fixed || variable) || !['G', 'ML', 'UNIT'].includes(i.unit) ||
        i.unit !== (fixed ? net.unit : 'G')) missing.push('unitOrPackage');
    else signals.push('unitOrPackage');

    if (pack.gtin != null && !gtinKey(pack.gtin)) missing.push('invalidGtin');
    if (i.target != null) {
        const target = i.target;
        if (!gtinKey(target.gtin) || !gtinKey(pack.gtin)) missing.push('targetGtin');
        else if (gtinKey(target.gtin) !== gtinKey(pack.gtin)) conflicts.push('targetGtin');
        else signals.push('targetGtin');
        if (!fixed || !isAmount(target.amount) || !Number.isSafeInteger(target.unitsPerPackage)) missing.push('targetPack');
        else if (Number(target.amount) !== Number(net.amount) || target.unitsPerPackage !== pack.unitsPerPackage)
            conflicts.push('targetPack');
        if (target.brand != null) compare('brand', target.brand, product.brand);
    }
    const sameName = normalize(i.name) === normalize(product.name);
    const score = Math.min(100, signals.length * 9 + (sameName ? 10 : 0));
    const substitute = conflicts.includes('concept') && conflicts.every(r => ['concept', 'ingredients'].includes(r)) &&
        list(i.substituteConcepts) && i.substituteConcepts.includes(facts.concept);
    const unreliable = missing.includes('canonicalReview') || missing.includes('labelReview');
    const kind = unreliable ? null : conflicts.length ? (substitute ? 'SUSTITUTO' : 'NO_COMPATIBLE')
        : missing.length ? null : i.target ? 'MISMO_PRODUCTO' : 'EQUIVALENTE';
    const status = kind === 'NO_COMPATIBLE' ? 'REJECTED'
        : !kind || substitute || score < threshold ? 'PENDING' : 'ACCEPTED';
    return { kind, status, score, threshold, signals, sameName, missing, conflicts };
}
