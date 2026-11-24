// Simulated editorial profiles. Columns: category, concept, preparation, composition,
// form, content basis, dietary variant, ordered ingredients, purchase unit.
import { labelDigest } from '../matching-evidence.mjs';

const profiles = {
    arroz: ['cereal','rice','raw','brown','grain','edible','plain',['brown-rice'],'G'],
    leche: ['dairy','milk','UHT','whole','liquid','net','lactose',['milk'],'ML'],
    yogur: ['dairy','yogurt','fermented','whole','cup','net','unsweetened',['milk','cultures'],'G'],
    huevos: ['eggs','hen-egg','raw','class-M','shell','gross','plain',['egg'],'UNIT'],
    aceite: ['oils','olive','pressed','extra-virgin','liquid','net','plain',['olive-oil'],'ML'],
    pasta: ['cereal','pasta','dry','durum','spaghetti','edible','standard',['wheat'],'G'],
    pollo: ['meat','chicken','raw','breast','boneless','edible','plain',['chicken'],'G'],
    conserva: ['fish','tuna','canned','in-water','flakes','drained','plain',['tuna','water'],'G'],
};
const fields = ['category','concept','preparation','composition','form','basis','diet','ingredients','unit'];
const gtin = '0036000291452';
const target = { gtin, amount: '1000', unitsPerPackage: 1, brand: 'Marca A' };

// Independent expected decisions. Each row represents one editorial comparison.
export const cases = [
    ['arroz', 'integral crudo', 'CULINARY_EQUIVALENT'],
    ['arroz', 'precocinado', 'INCOMPATIBLE', { preparation: 'boiled' }],
    ['arroz', 'estado BEDCA 2660 sin resolver', 'PENDING', {}, { preparation: 'UNSPECIFIED' }],
    ['leche', 'entera marca A', 'CULINARY_EQUIVALENT'],
    ['leche', 'entera marca B', 'CULINARY_EQUIVALENT', {}, {}, { brand: 'Marca B' }],
    ['leche', 'semidesnatada', 'INCOMPATIBLE', { composition: 'semi-skimmed' }],
    ['leche', 'sin lactosa', 'INCOMPATIBLE', { diet: 'lactose-free' }],
    ['leche', 'mismo envase', 'EXACT_SKU', {}, { target }, { gtin: '00036000291452' }],
    ['leche', 'GTIN igual con formato contradictorio', 'INCOMPATIBLE', {}, { target }, { gtin, amount: '500' }],
    ['yogur', 'natural', 'CULINARY_EQUIVALENT'],
    ['yogur', 'azucarado', 'INCOMPATIBLE', { diet: 'sweetened', ingredients: ['milk','sugar','cultures'] }],
    ['huevos', 'por unidades', 'CULINARY_EQUIVALENT'],
    ['huevos', 'por peso sin factor', 'PENDING', {}, {}, { unit: 'G' }],
    ['aceite', 'virgen extra', 'CULINARY_EQUIVALENT'],
    ['aceite', 'sin grado', 'PENDING', { composition: null }],
    ['pasta', 'sémola', 'CULINARY_EQUIVALENT'],
    ['pasta', 'trazas de huevo', 'INCOMPATIBLE', { allergens: { egg: 'MAY_CONTAIN' } }, { excludeAllergens: ['egg'] }],
    ['pasta', 'huevo sin declarar', 'PENDING', {}, { excludeAllergens: ['egg'] }],
    ['pasta', 'ausencia documentada', 'CULINARY_EQUIVALENT', { allergens: { egg: 'FREE' } }, { excludeAllergens: ['egg'] }],
    ['pollo', 'pechuga al peso', 'CULINARY_EQUIVALENT', {}, {}, { variable: true }],
    ['pollo', 'pavo autorizado como alternativa', 'ALLOWED_SUBSTITUTE', { concept: 'turkey', ingredients: ['turkey'] },
        { substituteConcepts: ['turkey'] }],
    ['conserva', 'contenido escurrido', 'CULINARY_EQUIVALENT'],
    ['conserva', 'peso con líquido', 'INCOMPATIBLE', { basis: 'net' }],
    ['conserva', 'base sin declarar', 'PENDING', { basis: null }],
];

export function comparison([food, title, expected, factsPatch, canonicalPatch, options = {}]) {
    const profile = Object.fromEntries(fields.map((field, n) => [field, profiles[food][n]]));
    const ingredient = { key: food, name: food, reviewed: true, catalogState: 'VISIBLE', canonicalId: 'simulated.' + food,
        revision: 'fixture-1', ...profile, excludeAllergens: [], ...canonicalPatch };
    const origin = { sourceId: 'fixture', recordKey: food };
    const product = { id: food, origin, name: food, brand: options.brand ?? 'Marca A', attributes: {} };
    const pack = { id: 'pack.' + food, origin, productId: food, gtin: options.gtin ?? null,
        weightMode: options.variable ? 'VARIABLE' : 'FIXED', saleUnit: options.variable ? 'KG' : 'PACKAGE',
        netContent: options.variable ? null : { amount: options.amount ?? '1000', unit: options.unit ?? profile.unit },
        unitsPerPackage: options.variable ? null : 1 };
    const item = { product, package: pack };
    item.facts = { ...profile, reviewed: true, sourceRef: 'simulated label / ' + title,
        labelDigest: labelDigest(item), ...factsPatch };
    return { ingredients: [ingredient], catalog: [item] };
}
