import fs from "node:fs";
import path from "node:path";

const rootDir = "/Users/dmitry/Desktop/videocharter";
const dataDir = path.join(rootDir, "data");
const statePath = path.join(dataDir, "state.json");

const baseInstant = new Date("2026-07-07T09:00:00.000Z");

const curatedSeeds = [
  profileSeed(910000001, "anna_roma", "Anna", "FEMALE", "EVERYONE", "DATING", 24, 22, 33, "OPEN", "IT", "Italy", "🇮🇹"),
  profileSeed(910000002, "lucy_travel", "Lucy", "FEMALE", "EVERYONE", "FRIENDSHIP", 27, 23, 38, "OPEN", "GB", "United Kingdom", "🇬🇧"),
  profileSeed(910000003, "mila_latina", "Mila", "FEMALE", "MEN", "DATING", 26, 24, 36, "PRIVATE", "ES", "Spain", "🇪🇸"),
  profileSeed(910000004, "nora_coffee", "Nora", "FEMALE", "EVERYONE", "LANGUAGE_EXCHANGE", 29, 24, 40, "OPEN", "DE", "Germany", "🇩🇪"),
  profileSeed(910000005, "sofia_wave", "Sofia", "FEMALE", "MEN", "DATING", 23, 21, 34, "OPEN", "BR", "Brazil", "🇧🇷"),
  profileSeed(910000006, "emma_sunset", "Emma", "FEMALE", "EVERYONE", "NETWORKING", 31, 25, 42, "PRIVATE", "US", "United States", "🇺🇸"),
  profileSeed(910000007, "lina_nomad", "Lina", "FEMALE", "EVERYONE", "FRIENDSHIP", 28, 23, 39, "OPEN", "FR", "France", "🇫🇷"),
  profileSeed(910000008, "maya_talks", "Maya", "FEMALE", "WOMEN", "DATING", 25, 22, 36, "OPEN", "CA", "Canada", "🇨🇦"),
  profileSeed(910000009, "eva_mornings", "Eva", "FEMALE", "EVERYONE", "LANGUAGE_EXCHANGE", 30, 24, 41, "PRIVATE", "NL", "Netherlands", "🇳🇱"),
  profileSeed(910000010, "daria_trip", "Daria", "FEMALE", "EVERYONE", "DATING", 22, 20, 32, "OPEN", "PL", "Poland", "🇵🇱"),
  profileSeed(910000011, "alex_coast", "Alex", "MALE", "EVERYONE", "DATING", 27, 21, 36, "OPEN", "AU", "Australia", "🇦🇺"),
  profileSeed(910000012, "leo_street", "Leo", "MALE", "WOMEN", "DATING", 29, 22, 39, "OPEN", "IT", "Italy", "🇮🇹"),
  profileSeed(910000013, "daniel_walks", "Daniel", "MALE", "EVERYONE", "FRIENDSHIP", 32, 24, 43, "PRIVATE", "SE", "Sweden", "🇸🇪"),
  profileSeed(910000014, "noah_calm", "Noah", "MALE", "EVERYONE", "NETWORKING", 34, 24, 45, "OPEN", "US", "United States", "🇺🇸"),
  profileSeed(910000015, "marco_city", "Marco", "MALE", "WOMEN", "DATING", 26, 21, 35, "OPEN", "ES", "Spain", "🇪🇸"),
  profileSeed(910000016, "sam_stories", "Sam", "MALE", "EVERYONE", "LANGUAGE_EXCHANGE", 28, 22, 38, "PRIVATE", "DE", "Germany", "🇩🇪"),
  profileSeed(910000017, "kai_routes", "Kai", "OTHER", "EVERYONE", "FRIENDSHIP", 27, 20, 39, "OPEN", "JP", "Japan", "🇯🇵"),
  profileSeed(910000018, "jules_frame", "Jules", "OTHER", "EVERYONE", "NETWORKING", 30, 23, 42, "OPEN", "FR", "France", "🇫🇷"),
  profileSeed(910000019, "ryan_views", "Ryan", "MALE", "EVERYONE", "DATING", 24, 20, 34, "OPEN", "CA", "Canada", "🇨🇦"),
  profileSeed(910000020, "ethan_late", "Ethan", "MALE", "EVERYONE", "FRIENDSHIP", 33, 24, 44, "PRIVATE", "GB", "United Kingdom", "🇬🇧")
];

const generatedSeeds = buildGeneratedSeeds(520, 920000001);
const seeds = [...curatedSeeds, ...generatedSeeds];

function profileSeed(userId, username, name, gender, lookingFor, goal, age, preferredAgeMin, preferredAgeMax, privacyMode, countryCode, countryName, countryFlag) {
  return {
    userId,
    username,
    name,
    gender,
    lookingFor,
    goal,
    age,
    preferredAgeMin,
    preferredAgeMax,
    privacyMode,
    countryCode,
    countryName,
    countryFlag
  };
}

function buildGeneratedSeeds(total, startingUserId) {
  const femaleNames = [
    "Alina", "Anastasia", "Arina", "Dasha", "Elena", "Irina", "Karina", "Ksenia", "Lera", "Liza",
    "Maria", "Milana", "Nika", "Polina", "Sasha", "Tanya", "Vera", "Yana", "Zoya", "Amina"
  ];
  const maleNames = [
    "Alex", "Artem", "Bogdan", "Daniil", "Denis", "Egor", "Ilya", "Kirill", "Lev", "Maksim",
    "Mikhail", "Nikita", "Oleg", "Pavel", "Roman", "Stepan", "Timur", "Vadim", "Vlad", "Yaroslav"
  ];
  const otherNames = [
    "Ari", "Kai", "Noel", "Robin", "Sky", "Taylor", "Jules", "Rin", "Sage", "Nico"
  ];
  const countries = [
    { code: "RU", name: "Russia", flag: "🇷🇺", weight: 14 },
    { code: "KZ", name: "Kazakhstan", flag: "🇰🇿", weight: 4 },
    { code: "BY", name: "Belarus", flag: "🇧🇾", weight: 3 },
    { code: "UA", name: "Ukraine", flag: "🇺🇦", weight: 3 },
    { code: "GE", name: "Georgia", flag: "🇬🇪", weight: 2 },
    { code: "AM", name: "Armenia", flag: "🇦🇲", weight: 2 },
    { code: "UZ", name: "Uzbekistan", flag: "🇺🇿", weight: 2 },
    { code: "TR", name: "Turkey", flag: "🇹🇷", weight: 2 },
    { code: "DE", name: "Germany", flag: "🇩🇪", weight: 2 },
    { code: "PL", name: "Poland", flag: "🇵🇱", weight: 2 },
    { code: "RS", name: "Serbia", flag: "🇷🇸", weight: 1 },
    { code: "AE", name: "United Arab Emirates", flag: "🇦🇪", weight: 1 },
    { code: "TH", name: "Thailand", flag: "🇹🇭", weight: 1 },
    { code: "IT", name: "Italy", flag: "🇮🇹", weight: 1 }
  ];
  const weightedCountries = countries.flatMap(country => Array.from({ length: country.weight }, () => country));
  const seeds = [];

  for (let index = 0; index < total; index += 1) {
    const userId = startingUserId + index;
    const gender = pickGender(index);
    const name = pickName(gender, index, femaleNames, maleNames, otherNames);
    const username = slugify(`${name}_${userId}`);
    const country = weightedCountries[index % weightedCountries.length];
    const age = 18 + (index % 18);
    const goal = pickGoal(index);
    const lookingFor = pickLookingFor(index);
    const privacyMode = index % 5 === 0 ? "PRIVATE" : "OPEN";
    const preferredAgeMin = Math.max(18, age - (2 + (index % 4)));
    const preferredAgeMax = Math.min(45, age + 10 + (index % 7));

    seeds.push(profileSeed(
      userId,
      username,
      name,
      gender,
      lookingFor,
      goal,
      age,
      preferredAgeMin,
      preferredAgeMax,
      privacyMode,
      country.code,
      country.name,
      country.flag
    ));
  }

  return seeds;
}

function pickGender(index) {
  if (index % 17 === 0) {
    return "OTHER";
  }
  return index % 2 === 0 ? "FEMALE" : "MALE";
}

function pickName(gender, index, femaleNames, maleNames, otherNames) {
  const source = gender === "FEMALE"
    ? femaleNames
    : gender === "MALE"
      ? maleNames
      : otherNames;
  return source[index % source.length];
}

function pickGoal(index) {
  const goals = [
    "DATING", "DATING", "DATING", "FRIENDSHIP",
    "LANGUAGE_EXCHANGE", "NETWORKING"
  ];
  return goals[index % goals.length];
}

function pickLookingFor(index) {
  if (index % 9 === 0) {
    return "WOMEN";
  }
  if (index % 11 === 0) {
    return "MEN";
  }
  return "EVERYONE";
}

function slugify(value) {
  return value.toLowerCase().replace(/[^a-z0-9_]+/g, "_");
}

function ensureStateShape(value) {
  return {
    profiles: value?.profiles ?? {},
    users: value?.users ?? {},
    likesBySource: value?.likesBySource ?? {},
    dismissedLikesByTarget: value?.dismissedLikesByTarget ?? {},
    countryPopularity: value?.countryPopularity ?? {},
    matches: value?.matches ?? [],
    reports: value?.reports ?? [],
    nextReportId: value?.nextReportId ?? 1,
    monthlySubscriptionStars: value?.monthlySubscriptionStars ?? 30,
    yearlySubscriptionStars: value?.yearlySubscriptionStars ?? 300
  };
}

function mediaFor(seed, index) {
  const slug = `${seed.username}-${seed.userId}`;
  const urls = [
    `https://picsum.photos/seed/${slug}-1/960/1280`,
    `https://picsum.photos/seed/${slug}-2/960/1280`,
    `https://picsum.photos/seed/${slug}-3/960/1280`
  ];

  if (index % 5 === 0) {
    return [
      { type: "PHOTO", fileId: urls[0] },
      { type: "PHOTO", fileId: urls[1] },
      { type: "PHOTO", fileId: urls[2] }
    ];
  }
  if (index % 2 === 0) {
    return [
      { type: "PHOTO", fileId: urls[0] },
      { type: "PHOTO", fileId: urls[1] }
    ];
  }
  return [{ type: "PHOTO", fileId: urls[0] }];
}

function updatedAtFor(index) {
  return new Date(baseInstant.getTime() - index * 7 * 60 * 1000).toISOString();
}

fs.mkdirSync(dataDir, { recursive: true });

let state = ensureStateShape({});
if (fs.existsSync(statePath)) {
  state = ensureStateShape(JSON.parse(fs.readFileSync(statePath, "utf8")));
}

let inserted = 0;
for (const [index, seed] of seeds.entries()) {
  const key = String(seed.userId);
  const timestamp = updatedAtFor(index);

  if (!state.users[key]) {
    inserted += 1;
  }

  state.users[key] = {
    ...(state.users[key] ?? {}),
    userId: seed.userId,
    username: seed.username,
    firstName: seed.name,
    admin: false,
    moderator: false,
    banned: false,
    subscriptionUntil: state.users[key]?.subscriptionUntil ?? null,
    lastViewDate: state.users[key]?.lastViewDate ?? null,
    viewsToday: state.users[key]?.viewsToday ?? 0
  };

  state.profiles[key] = {
    userId: seed.userId,
    username: seed.username,
    name: seed.name,
    gender: seed.gender,
    lookingFor: seed.lookingFor,
    goal: seed.goal,
    age: seed.age,
    preferredAgeMin: seed.preferredAgeMin,
    preferredAgeMax: seed.preferredAgeMax,
    privacyMode: seed.privacyMode,
    countryCode: seed.countryCode,
    countryName: seed.countryName,
    countryFlag: seed.countryFlag,
    media: mediaFor(seed, index),
    createdAt: state.profiles[key]?.createdAt ?? timestamp,
    updatedAt: timestamp
  };

  state.countryPopularity[seed.countryCode] = Math.max(
    Number(state.countryPopularity[seed.countryCode] ?? 0),
    1
  );
}

fs.writeFileSync(statePath, `${JSON.stringify(state, null, 2)}\n`, "utf8");

console.log(`Seeded ${inserted} fake accounts into ${statePath}`);
console.log(`Total profiles in state: ${Object.keys(state.profiles).length}`);
