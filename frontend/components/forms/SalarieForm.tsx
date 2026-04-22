"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useForm, type Resolver } from "react-hook-form";
import { z } from "zod";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type { SalarieRequest } from "@/lib/types/rh";

const emptyStr = (v: unknown) => (v == null ? "" : String(v));

/** RHF peut envoyer `undefined` sur champs jamais touchés — normaliser avant `z.string()`. */
const schema = z.object({
  nom: z.preprocess(emptyStr, z.string().min(1, "Requis")),
  prenom: z.preprocess(emptyStr, z.string().min(1, "Requis")),
  email: z.preprocess((v) => emptyStr(v).trim(), z.string().refine((s) => s === "" || z.string().email().safeParse(s).success, "Email invalide")),
  telephone: z.preprocess(emptyStr, z.string()),
  poste: z.preprocess(emptyStr, z.string().min(1, "Requis")),
  service: z.preprocess(emptyStr, z.string().min(1, "Requis")),
  dateEmbauche: z.preprocess(emptyStr, z.string().min(1, "Requis")),
  typeContrat: z.preprocess(emptyStr, z.string().min(1, "Requis")),
  nationalite: z.preprocess(emptyStr, z.string()),
  adresse: z.preprocess(emptyStr, z.string()),
  montantBrut: z.coerce.number().positive({ message: "Montant invalide" }),
  montantNet: z.coerce.number().positive({ message: "Montant invalide" }),
  devise: z.preprocess((v) => {
    const s = emptyStr(v).trim().toUpperCase();
    return s === "" ? "EUR" : s;
  }, z.string().length(3, "3 lettres (ex. EUR)")),
});

export type SalarieFormValues = z.infer<typeof schema>;

function toRequest(v: SalarieFormValues): SalarieRequest {
  const em = v.email?.trim();
  return {
    nom: v.nom,
    prenom: v.prenom,
    email: em && em.length > 0 ? em : null,
    telephone: v.telephone || null,
    poste: v.poste,
    service: v.service,
    dateEmbauche: v.dateEmbauche,
    typeContrat: v.typeContrat,
    nationalite: v.nationalite || null,
    adresse: v.adresse || null,
    montantBrut: v.montantBrut,
    montantNet: v.montantNet,
    devise: v.devise.toUpperCase(),
  };
}

export function SalarieForm({
  defaultValues,
  onSubmit,
  submitLabel = "Enregistrer",
  salaireEditable = true,
}: {
  defaultValues?: Partial<SalarieFormValues>;
  onSubmit: (data: SalarieRequest) => Promise<void>;
  submitLabel?: string;
  /** En édition, le salaire doit passer par "Nouvelle grille" (PRD). */
  salaireEditable?: boolean;
}) {
  const mergedDefaults = defaultValues
    ? Object.fromEntries(Object.entries(defaultValues).filter(([, v]) => v !== undefined))
    : {};

  const form = useForm<SalarieFormValues>({
    resolver: zodResolver(schema) as Resolver<SalarieFormValues>,
    defaultValues: {
      nom: "",
      prenom: "",
      email: "",
      telephone: "",
      poste: "",
      service: "",
      dateEmbauche: new Date().toISOString().slice(0, 10),
      typeContrat: "CDI",
      nationalite: "",
      adresse: "",
      montantBrut: 3000,
      montantNet: 2400,
      devise: "EUR",
      ...mergedDefaults,
    },
  });

  return (
    <form
      className="grid max-h-[70vh] grid-cols-1 gap-3 overflow-y-auto md:grid-cols-2"
      onSubmit={form.handleSubmit(async (v) => {
        await onSubmit(toRequest(v));
      })}
    >
      <div>
        <Label htmlFor="nom">Nom</Label>
        <Input id="nom" {...form.register("nom")} />
        {form.formState.errors.nom && (
          <p className="text-xs text-red-600">{form.formState.errors.nom.message}</p>
        )}
      </div>
      <div>
        <Label htmlFor="prenom">Prénom</Label>
        <Input id="prenom" {...form.register("prenom")} />
      </div>
      <div>
        <Label htmlFor="email">Email</Label>
        <Input id="email" type="email" {...form.register("email")} />
      </div>
      <div>
        <Label htmlFor="telephone">Téléphone</Label>
        <Input id="telephone" {...form.register("telephone")} />
      </div>
      <div>
        <Label htmlFor="poste">Poste</Label>
        <Input id="poste" {...form.register("poste")} />
      </div>
      <div>
        <Label htmlFor="service">Service</Label>
        <Input id="service" {...form.register("service")} />
      </div>
      <div>
        <Label htmlFor="dateEmbauche">Date embauche</Label>
        <Input id="dateEmbauche" type="date" {...form.register("dateEmbauche")} />
      </div>
      <div>
        <Label htmlFor="typeContrat">Type contrat</Label>
        <Input id="typeContrat" {...form.register("typeContrat")} />
      </div>
      <div>
        <Label htmlFor="nationalite">Nationalité</Label>
        <Input id="nationalite" {...form.register("nationalite")} />
      </div>
      <div className="md:col-span-2">
        <Label htmlFor="adresse">Adresse</Label>
        <Input id="adresse" {...form.register("adresse")} />
      </div>
      <div>
        <Label htmlFor="montantBrut">Montant brut</Label>
        <Input id="montantBrut" type="number" step="0.01" disabled={!salaireEditable} {...form.register("montantBrut")} />
      </div>
      <div>
        <Label htmlFor="montantNet">Montant net</Label>
        <Input id="montantNet" type="number" step="0.01" disabled={!salaireEditable} {...form.register("montantNet")} />
      </div>
      <div>
        <Label htmlFor="devise">Devise</Label>
        <Input id="devise" maxLength={3} disabled={!salaireEditable} {...form.register("devise")} />
        {!salaireEditable ? (
          <p className="mt-1 text-xs text-slate-500">
            Pour modifier le salaire, utilisez <span className="font-medium">Nouvelle grille</span> (historique salarial).
          </p>
        ) : null}
      </div>
      <div className="md:col-span-2 flex justify-end gap-2 pt-2">
        <Button type="submit" disabled={form.formState.isSubmitting}>
          {submitLabel}
        </Button>
      </div>
    </form>
  );
}
